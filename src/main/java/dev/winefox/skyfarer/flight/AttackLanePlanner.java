package dev.winefox.skyfarer.flight;

/** Legacy entry/contact/exit geometry, detached from the entity and world. */
public final class AttackLanePlanner {
    private AttackLanePlanner() {}
    public record Input(FlightPrediction.State start,double x,double y,double z,double dx,double dz,
        SlopeSampler.Estimate slopes,double speed,double gravity,double minimum,double exit,double expansion,
        double levelDistance,double side,double vertical,boolean fuel) {}
    public record Point(double x,double y,double z) {}
    public record Lane(double slope,double flare,double offset,double gradientX,double gradientZ,
        Point entry,Point exit,Point direction,String description) {}
    @FunctionalInterface public interface Space {boolean clear(double ax,double ay,double az,double bx,double by,double bz,double side,double vertical);}
    public static Lane choose(Input r,Space space){
        var s=r.start;double distance=Math.hypot(r.x-s.x(),r.z-s.z()),rise=s.y()-r.y;
        boolean flat=r.slopes.samples()>=12&&Math.hypot(r.slopes.nearX(),r.slopes.nearZ())<.16&&Math.hypot(r.slopes.wideX(),r.slopes.wideZ())<.16;
        if(flat&&rise>=3&&distance>=DiveProfile.minimumDistance(rise,r.speed,r.gravity)&&rise/distance<=1.2){
            double slope=DiveProfile.slope(rise,distance);Point last=new Point(s.x(),s.y(),s.z());boolean clear=true;
            for(double d=Math.max(0,distance-1);;d=Math.max(0,d-1)){
                Point p=new Point(r.x-r.dx*d,FlightPrediction.pathHeight(d,r.y,slope,-2),r.z-r.dz*d);
                if(!clear(space,last,p,r)){clear=false;break;}last=p;if(d==0)break;
            }
            Point exit=new Point(last.x+r.dx*r.exit*.65,last.y+4,last.z+r.dz*r.exit*.65);
            if(clear&&clear(space,last,exit,r)){
                double d=Math.max(0,distance-1);
                return new Lane(slope,-2,0,0,0,new Point(s.x(),s.y(),s.z()),exit,
                    new Point(r.x-r.dx*d-s.x(),FlightPrediction.pathHeight(d,r.y,slope,-2)-s.y(),r.z-r.dz*d-s.z()),"curved dive; rise="+rise+", distance="+distance);
            }
        }
        AttackLinePlanner.Line best=null;double bestX=0,bestZ=0;
        for(int fit=0;fit<2;fit++){
            double gx=fit==0?r.slopes.nearX():r.slopes.wideX(),gz=fit==0?r.slopes.nearZ():r.slopes.wideZ();
            if(fit==1&&Math.hypot(gx-r.slopes.nearX(),gz-r.slopes.nearZ())<.025)continue;
            double gradient=Math.clamp(gx*r.dx+gz*r.dz,-1.5,1.5);
            if(Math.abs(gradient)<=.06&&s.y()>=r.y-.2)continue;
            var line=AttackLinePlanner.choose(s,r.x,r.y,r.z,r.dx,r.dz,gradient,-gx*r.dz+gz*r.dx,r.minimum,r.exit,r.expansion,r.fuel,
                (ax,ay,az,bx,by,bz)->space.clear(ax,ay,az,bx,by,bz,r.side,r.vertical));
            if(line!=null&&(best==null||line.cost()<best.cost())){best=line;bestX=gx;bestZ=gz;}
        }
        if(best!=null)return new Lane(-best.gradient(),-1,best.height()-r.y,bestX,bestZ,
            new Point(best.entryX(),best.entryY(),best.entryZ()),new Point(best.exitX(),best.exitY(),best.exitZ()),
            new Point(r.x-best.entryX(),best.height()-best.entryY(),r.z-best.entryZ()),"slope="+best.gradient()+", samples="+r.slopes.samples()+", routeCost="+best.cost());
        double spatial=Math.sqrt(distance*distance+rise*rise);
        for(double extra:new double[]{0,.5,1,1.5,2}){
            if(extra>r.expansion)break;
            Point contact=new Point(r.x,r.y+extra,r.z);double d=Math.min(r.levelDistance,spatial*.7);
            Point entry=new Point(r.x-r.dx*d,contact.y,r.z-r.dz*d),origin=new Point(s.x(),s.y(),s.z());
            if(!clear(space,origin,entry,r)||!clear(space,entry,contact,r))continue;
            for(double turn:new double[]{0,-35,35,-65,65})for(double up:new double[]{0,3,6})for(double delay:new double[]{0,3,6}){
                double a=Math.toRadians(turn),dx=r.dx*Math.cos(a)-r.dz*Math.sin(a),dz=r.dx*Math.sin(a)+r.dz*Math.cos(a);
                Point mid=new Point(contact.x+r.dx*delay,contact.y,contact.z+r.dz*delay);
                var capped=AttackExitPlanner.cap(contact.x,contact.y,contact.z,mid.x+dx*Math.max(0,r.exit-delay),mid.y+up,mid.z+dz*Math.max(0,r.exit-delay),r.exit);
                Point exit=new Point(capped.x(),capped.y(),capped.z());
                if(clear(space,contact,mid,r)&&clear(space,mid,exit,r))return new Lane(0,r.levelDistance,extra,0,0,origin,exit,
                    new Point(entry.x-origin.x,entry.y-origin.y,entry.z-origin.z),"clear entry/contact/exit; offset="+extra+", rise="+up+", turn="+turn);
            }
        }
        return null;
    }
    private static boolean clear(Space s,Point a,Point b,Input r){return s.clear(a.x,a.y,a.z,b.x,b.y,b.z,r.side,r.vertical);}
}
