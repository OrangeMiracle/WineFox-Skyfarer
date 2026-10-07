package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** TridentItem.releaseUsing additive impulse + 10 use ticks + 20 spin ticks + recovery.
 * Auxiliary propulsion never carries an attack certificate across a changed velocity. */
public final class RiptidePlanner {
    public static final int WINDUP=10, SPIN=20, RECOVERY=12;
    public record Plan(double yaw,double pitch,double height,double goalX,double goalZ,float strength,double cost,List<FlightPrediction.State> trace) {
        public Plan {trace=List.copyOf(trace);}
    }
    private RiptidePlanner() {}
    public static VanillaElytra.Look impulse(double yaw,double pitch,float strength) {
        float y=(float)yaw*(float)(Math.PI/180),p=(float)pitch*(float)(Math.PI/180);
        float x=-VanillaElytra.sin(y)*VanillaElytra.cos(p),v=-VanillaElytra.sin(p),z=VanillaElytra.cos(y)*VanillaElytra.cos(p);
        float length=(float)Math.sqrt(x*x+v*v+z*z);
        x*=strength/length;v*=strength/length;z*=strength/length;
        return new VanillaElytra.Look(x,v,z);
    }
    public static FlightPrediction.State push(FlightPrediction.State s,float strength) {
        var d=impulse(s.yaw(),s.pitch(),strength);
        return new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx()+d.x(),s.vy()+d.y(),s.vz()+d.z(),s.yaw(),s.pitch(),s.physics(),s.rocketDelay());
    }
    public static double pitch(FlightPrediction.State s,Plan p,int elapsed,double gravity) {
        return elapsed<=WINDUP?p.pitch():GlidePlanner.feedbackPitch(s,p.height(),0,gravity,false);
    }
    public static FlightPrediction.State step(FlightPrediction.State s,Plan p,int elapsed,double gravity,boolean wet) {
        double yaw=FlightPrediction.turn(s.yaw(),p.yaw(),8),pitch=FlightPrediction.turn(s.pitch(),pitch(s,p,elapsed,gravity),8);
        var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),yaw,pitch,s.physics(),s.rocketDelay());
        if(elapsed==WINDUP)aimed=push(aimed,p.strength());
        return SurfaceLaunchPlanner.step(aimed,yaw,pitch,gravity,false,wet);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        return choose(s,x,height,z,strength,gravity,space,water,0);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water,int elapsed) {
        if(!(strength>0)||!Float.isFinite(strength))return null;
        double yaw=Math.toDegrees(Math.atan2(s.x()-x,z-s.z()));Plan best=null;
        for(double pitch:elapsed==WINDUP?new double[]{-35,-55,-70}:new double[]{-15,-35,-55}) {
            var start=elapsed==WINDUP?aimLaunch(s,yaw,pitch):s;
            var p=certify(start,new Plan(yaw,pitch,height,x,z,strength,0,List.of()),elapsed,gravity,space,water);
            if(p!=null&&(best==null||p.cost()<best.cost()))best=p;
        }
        return best;
    }
    public static FlightPrediction.State aimLaunch(FlightPrediction.State s,double yaw,double pitch) {
        return new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),yaw,pitch,s.physics(),s.rocketDelay());
    }
    /** Revalidate the remaining path from CURRENT velocity before beginning or releasing the item. */
    public static Plan certify(FlightPrediction.State s,Plan p,int elapsed,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        var path=new ArrayList<FlightPrediction.State>();path.add(s);double cost=0;
        double dx=-Math.sin(Math.toRadians(p.yaw())),dz=Math.cos(Math.toRadians(p.yaw()));
        for(int tick=elapsed;tick<WINDUP+SPIN+RECOVERY;tick++) {
            var next=step(s,p,tick,gravity,water.wet(s));
            if(!space.clear(s,next,.2,.15))return null;
            // Leave room for a fresh spear windup / landing turn instead of overshooting the destination.
            if((p.goalX()-next.x())*dx+(p.goalZ()-next.z())*dz<6)return null;
            cost+=Math.pow(Math.max(0,p.height()-next.y()),2)*.01;
            s=next;path.add(s);
        }
        if(water.wet(s)||Math.hypot(s.vx(),s.vz())<.55)return null;
        return new Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),cost+1,List.copyOf(path));
    }
}
