package dev.winefox.skyfarer.flight;

/** Signed attack lines. Climb and descent have no type penalty; distance, turning and fuel do. */
public final class AttackLinePlanner {
    private AttackLinePlanner() {}
    @FunctionalInterface public interface Space { boolean clear(double ax,double ay,double az,double bx,double by,double bz); }
    public record Line(double gradient,double height,double entryX,double entryY,double entryZ,
        double exitX,double exitY,double exitZ,double cost) {}
    public static double gradient(double low,double center,double high,double spacing) {
        if(Double.isFinite(low) && Double.isFinite(high)) return Math.clamp((high-low)/(2*spacing),-1.5,1.5);
        if(Double.isFinite(high)) return Math.clamp((high-center)/spacing,-1.5,1.5);
        if(Double.isFinite(low)) return Math.clamp((center-low)/spacing,-1.5,1.5);
        return 0;
    }
    public static Line choose(FlightPrediction.State s,double x,double y,double z,double dx,double dz,
        double groundGradient,double minimumEntry,double exitDistance,double expansion,boolean pulseAvailable,Space space) {
        return choose(s,x,y,z,dx,dz,groundGradient,0,minimumEntry,exitDistance,expansion,pulseAvailable,space);
    }
    public static Line choose(FlightPrediction.State s,double x,double y,double z,double dx,double dz,
        double groundGradient,double crossGradient,double minimumEntry,double exitDistance,double expansion,boolean pulseAvailable,Space space) {
        double distance=Math.hypot(x-s.x(),z-s.z()),direct=Math.clamp((y-s.y())/Math.max(1,distance),-1.5,1.5);
        Line best=null;
        for(double grade:new double[]{groundGradient,direct,0,Math.clamp(groundGradient+.2,-1.5,1.5),Math.clamp(groundGradient-.2,-1.5,1.5)})
            for(double extra:new double[]{0,.5,1,1.5,2,2.5}) {
                if(extra>expansion) continue;
                double contact=y+extra;
                double projection=((x-s.x())*dx+(contact-s.y())*grade+(z-s.z())*dz)/(1+grade*grade);
                double entry=Math.clamp(projection,minimumEntry,Math.max(minimumEntry,distance));
                double ex=x-dx*entry,ey=contact-grade*entry,ez=z-dz*entry;
                if(!space.clear(s.x(),s.y(),s.z(),ex,ey,ez) || !space.clear(ex,ey,ez,x,contact,z)) continue;
                for(double turn:new double[]{0,-35,35,-65,65,-85,85}) for(double lift:new double[]{0,3,6}) {
                    double a=Math.toRadians(turn),outX=dx*Math.cos(a)-dz*Math.sin(a),outZ=dx*Math.sin(a)+dz*Math.cos(a);
                    double outGrade=groundGradient*Math.cos(a)+crossGradient*Math.sin(a);
                    double ox=x+outX*exitDistance,oy=contact+outGrade*exitDistance+lift,oz=z+outZ*exitDistance;
                    if(!space.clear(x,contact,z,ox,oy,oz)) continue;
                    double approach=Math.sqrt((ex-s.x())*(ex-s.x())+(ey-s.y())*(ey-s.y())+(ez-s.z())*(ez-s.z()));
                    double vx=approach>2?ex-s.x():dx,vy=approach>2?ey-s.y():grade,vz=approach>2?ez-s.z():dz;
                    double speed=Math.sqrt(s.vx()*s.vx()+s.vy()*s.vy()+s.vz()*s.vz());
                    double cosine=(s.vx()*vx+s.vy()*vy+s.vz()*vz)/Math.max(.001,speed*Math.sqrt(vx*vx+vy*vy+vz*vz));
                    double turnCost=Math.toDegrees(Math.acos(Math.clamp(cosine,-1,1)))*.15;
                    double deficit=Math.max(0,.32-GlidePlanner.energy(s,Math.max(contact,oy),.08));
                    if(deficit>0 && !pulseAvailable) continue;
                    double cost=approach+entry*Math.sqrt(1+grade*grade)+Math.hypot(exitDistance,oy-contact)+turnCost+deficit*8+Math.abs(turn)*.04;
                    if(best==null || cost<best.cost) best=new Line(grade,contact,ex,ey,ez,ox,oy,oz,cost);
                }
            }
        return best;
    }
}
