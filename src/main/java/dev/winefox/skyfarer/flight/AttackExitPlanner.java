package dev.winefox.skyfarer.flight;

/** Combat recovery chooses a corridor; climbing is one option, not a mandatory action. */
public final class AttackExitPlanner {
    private AttackExitPlanner() {}
    public enum Style { STRAIGHT_CLIMB, TURN_CLIMB, TURN_GLIDE, STRAIGHT_GLIDE }
    @FunctionalInterface public interface Lane { boolean clear(double x,double y,double z); }
    public record Choice(double x,double y,double z,Style style,double cost) {}
    public static GlidePlanner.Exit cap(double sx,double sy,double sz,double x,double y,double z,double maximum) {
        double dx=x-sx,dy=y-sy,dz=z-sz,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double scale=length>maximum?maximum/length:1;
        return new GlidePlanner.Exit(sx+dx*scale,sy+dy*scale,sz+dz*scale);
    }
    public static Choice choose(FlightPrediction.State s,GlidePlanner.Request template,double recoveryY,
        double length,boolean piercing,double targetX,double targetZ,GlidePlanner.Corridor corridor,Lane lane) {
        Choice best=null;
        double yaw=Math.toDegrees(Math.atan2(-s.vx(),s.vz()));
        if (Math.hypot(s.vx(),s.vz())<.05) yaw=s.yaw();
        for (double turn:new double[]{0,-35,35,-65,65,-85,85}) for (boolean climb:new boolean[]{true,false}) {
            double a=Math.toRadians(yaw+turn), x=s.x()-Math.sin(a)*length,z=s.z()+Math.cos(a)*length,y=climb?recoveryY:s.y();
            var endpoint=cap(s.x(),s.y(),s.z(),x,y,z,length);
            x=endpoint.x(); y=endpoint.y(); z=endpoint.z();
            if (!lane.clear(x,y,z)) continue;
            var request=new GlidePlanner.Request(yaw+turn,y,0,0,14,Double.NaN,template.rocketTicks(),template.yawRate(),
                template.pitchRate(),template.recoveryRate(),template.gravity(),template.sideMargin(),template.verticalMargin(),true,null);
            var plan=GlidePlanner.choose(s,request,corridor);
            if (!GlidePlanner.ordinary(plan)) continue;
            double cost=plan.cost()*.02+Math.abs(turn)*.03+(climb?Math.max(0,recoveryY-s.y())*.1:0)+(piercing?Math.abs(turn)*.6:0);
            if (Double.isFinite(targetX) && Double.isFinite(targetZ)) {
                double desired=Math.toDegrees(Math.atan2(x-targetX,targetZ-z));
                cost+=Math.abs(FlightPrediction.turn(yaw+turn,desired,180)-(yaw+turn))*.05;
            }
            Style style=turn==0?(climb?Style.STRAIGHT_CLIMB:Style.STRAIGHT_GLIDE):(climb?Style.TURN_CLIMB:Style.TURN_GLIDE);
            if(best==null || cost<best.cost) best=new Choice(x,y,z,style,cost);
        }
        return best;
    }
}
