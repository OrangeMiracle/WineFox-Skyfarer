package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** WineFox metered Riptide: native charge/release entry, bounded per-tick thrust, vanilla travel.
 * A departure is certified for flight readiness, not for stopping before a nearby attack target. */
public final class RiptidePlanner {
    public static final int WINDUP=10, RECOVERY=12, DEPARTURE_RECOVERY=24;
    public static final float THRUST=.2f;
    /** Native I/II/III strengths 1.5/2.25/3 map to 10/20/30 ticks; unknown effects remain bounded. */
    public static int maximumTicks(float strength){return strength>0&&Float.isFinite(strength)?10*Math.max(1,Math.min(3,Math.round(strength/.75f-1))):0;}
    public record Plan(double yaw,double pitch,double height,double goalX,double goalZ,float strength,int duration,boolean departure,double cost,List<FlightPrediction.State> trace) {
        public Plan {trace=List.copyOf(trace);}
        public int end(){return WINDUP+duration;}
        public int handoff(){return end()+(departure?DEPARTURE_RECOVERY:RECOVERY);}
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
        return elapsed<p.end()?p.pitch():GlidePlanner.feedbackPitch(s,p.height(),0,gravity,false);
    }
    public static FlightPrediction.State step(FlightPrediction.State s,Plan p,int elapsed,double gravity,boolean wet) {
        double yaw=FlightPrediction.turn(s.yaw(),p.yaw(),8),pitch=FlightPrediction.turn(s.pitch(),pitch(s,p,elapsed,gravity),8);
        var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),yaw,pitch,s.physics(),s.rocketDelay());
        if(elapsed>=WINDUP&&elapsed<p.end())aimed=push(aimed,THRUST);
        return SurfaceLaunchPlanner.step(aimed,yaw,pitch,gravity,false,wet);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        return choose(s,x,height,z,strength,gravity,space,water,0);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water,int elapsed) {
        if(!(strength>0)||!Float.isFinite(strength))return null;
        boolean departure=elapsed==WINDUP;
        double yaw=Math.toDegrees(Math.atan2(s.x()-x,z-s.z()));
        // Every tick length is available, not a sparse set of duration presets. Stop at the shortest feasible duration.
        for(int duration=1;duration<=maximumTicks(strength);duration++) {
            Plan best=null;
            for(double turn:departure?new double[]{0,-90,90,180}:new double[]{0})
            for(double pitch:departure?new double[]{-35,-55,-70}:new double[]{-15,-35,-55}) {
                var start=departure?aimLaunch(s,yaw+turn,pitch):s;
                var p=certify(start,new Plan(yaw+turn,pitch,height,x,z,strength,duration,departure,0,List.of()),elapsed,gravity,space,water);
                if(p!=null&&(best==null||p.cost()<best.cost()))best=p;
            }
            if(best!=null)return best;
        }
        return null;
    }
    public static FlightPrediction.State aimLaunch(FlightPrediction.State s,double yaw,double pitch) {
        return new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),yaw,pitch,s.physics(),s.rocketDelay());
    }
    /** Revalidate the remaining path from CURRENT velocity before beginning or releasing the item. */
    public static Plan certify(FlightPrediction.State s,Plan p,int elapsed,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        if(p.duration()<1||p.duration()>maximumTicks(p.strength())||elapsed<0)return null;
        var path=new ArrayList<FlightPrediction.State>();path.add(s);double cost=0;
        double dx=-Math.sin(Math.toRadians(p.yaw())),dz=Math.cos(Math.toRadians(p.yaw()));
        for(int tick=elapsed;tick<p.handoff();tick++) {
            var next=step(s,p,tick,gravity,water.wet(s));
            if(!space.clear(s,next,.2,.15))return null;
            // Leave room for a fresh spear windup / landing turn instead of overshooting the destination.
            if(!p.departure()&&(p.goalX()-next.x())*dx+(p.goalZ()-next.z())*dz<6)return null;
            cost+=Math.pow(Math.max(0,p.height()-next.y()),2)*.01;
            s=next;path.add(s);
            // A departure must already have usable height AND speed when thrust stops.
            // Recovery cannot borrow an optimistic future climb to certify a weak hop.
            if(p.departure()&&tick==p.end()-1&&!departureReady(s,p))return null;
        }
        if(water.wet(s)||Math.hypot(s.vx(),s.vz())<.55||s.y()<p.height()-1||p.departure()&&s.vy()<-.5)return null;
        if(p.departure()&&(!departureReady(s,p)||!turnReserve(s,p,gravity,space,water)))return null;
        return new Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),p.duration(),p.departure(),cost+Math.abs(s.y()-p.height())*.02,List.copyOf(path));
    }
    public static boolean departureReady(FlightPrediction.State s,Plan p) {
        return s.y()>=p.height()-1&&Math.hypot(s.vx(),s.vz())>=.9&&s.vy()>=-.35;
    }
    /** Leave enough energy for a bounded turn and another native charge, without another boost.
     * One safe side is sufficient; the normal controller must still certify its actual next action. */
    private static boolean turnReserve(FlightPrediction.State start,Plan p,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        boolean safe=false;
        for(double turn:new double[]{-60,60}) {
            var s=start;boolean clear=true;
            for(int tick=0;tick<WINDUP+8;tick++) {
                double yaw=FlightPrediction.turn(s.yaw(),p.yaw()+turn,8);
                double pitch=FlightPrediction.turn(s.pitch(),0,8);
                var next=SurfaceLaunchPlanner.step(s,yaw,pitch,gravity,false,water.wet(s));
                if(!space.clear(s,next,.2,.15)||water.wet(next)||next.y()<p.height()-3){clear=false;break;}
                s=next;
            }
            safe|=clear&&Math.hypot(s.vx(),s.vz())>=.65;
        }
        return safe;
    }
}
