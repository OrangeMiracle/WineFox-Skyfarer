package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** WineFox metered Riptide: independent readiness and scoped item release, bounded per-tick thrust, vanilla travel.
 * A departure is certified for flight readiness, not for stopping before a nearby attack target. */
public final class RiptidePlanner {
    public static final int WINDUP=10, RECOVERY=12, DEPARTURE_RECOVERY=24;
    public static final float THRUST=.2f;
    /** Native I/II/III strengths 1.5/2.25/3 map to 10/20/30 ticks; unknown effects remain bounded. */
    public static int maximumTicks(float strength){return strength>0&&Float.isFinite(strength)?10*Math.max(1,Math.min(3,Math.round(strength/.75f-1))):0;}
    public record Plan(double yaw,double pitch,double height,double goalX,double goalZ,float strength,int duration,boolean departure,double minimumSpeed,double cost,List<FlightPrediction.State> trace,PropulsionDemand demand) {
        public Plan(double yaw,double pitch,double height,double x,double z,float strength,int duration,boolean departure,double minimumSpeed,double cost,List<FlightPrediction.State> trace){this(yaw,pitch,height,x,z,strength,duration,departure,minimumSpeed,cost,trace,null);}
        public Plan withDemand(PropulsionDemand d){return new Plan(yaw,pitch,height,goalX,goalZ,strength,duration,departure,minimumSpeed,cost,trace,d);}
        public Plan(double yaw,double pitch,double height,double x,double z,float strength,int duration,boolean departure,double cost,List<FlightPrediction.State> trace){this(yaw,pitch,height,x,z,strength,duration,departure,.55,cost,trace);}
        public Plan {trace=List.copyOf(trace);}
        public int end(){return WINDUP+duration;}
        public int handoff(){return end()+(departure?DEPARTURE_RECOVERY:RECOVERY);}
        public boolean guidedRecovery(){return demand!=null&&demand.continuation()?false:departure||minimumSpeed>.55;}
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
        var command=command(s,p,elapsed,gravity);
        double yaw=FlightPrediction.turn(s.yaw(),command.yaw(),command.yawRate()),pitch=FlightPrediction.turn(s.pitch(),command.pitch(),command.pitchRate());
        var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),yaw,pitch,s.physics(),s.rocketDelay());
        if(elapsed>=WINDUP&&elapsed<p.end())aimed=push(aimed,THRUST);
        return SurfaceLaunchPlanner.step(aimed,yaw,pitch,gravity,false,wet);
    }
    public static SweepingAttackPlanner.Command command(FlightPrediction.State s,Plan p,int elapsed,double gravity){
        if(p.demand()!=null&&p.demand().continuation()){
            var d=p.demand();return SweepingAttackPlanner.command(s,d.path(),d.aim(),d.exiting(),0,gravity,d.rate());
        }
        double rate=p.demand()!=null&&p.demand().emergency()?45:8;
        return new SweepingAttackPlanner.Command(p.yaw(),pitch(s,p,elapsed,gravity),rate,rate==45?20:8);
    }
    /** READY candidates start at release, explicitly separated from ground departure. */
    public static Plan choose(FlightPrediction.State s,PropulsionDemand d,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water){
        if(d.phase()==PropulsionDemand.Phase.REJOIN&&d.path()==null&&d.aim()!=null){
            var paths=SweepingAttackPlanner.interceptCandidates(s,new SweepingAttackPlanner.Point(d.aim().x(),d.aim().bottom()+d.aim().height()*.5,d.aim().z()),d.aim(),24);
            Plan best=null;
            // Bounded geometry seeds, each still proves the entire current-body strike and exit.
            int stride=Math.max(1,paths.size()/12);
            for(int i=0;i<paths.size();i+=stride){
                var requirement=new PropulsionDemand(d.phase(),d.x(),d.height(),d.z(),d.minimumSpeed(),paths.get(i),d.aim(),d.rate(),false,d.contactDeadline());
                var candidate=choose(s,requirement,strength,gravity,space,water);
                if(candidate!=null&&(best==null||candidate.duration()<best.duration()||candidate.duration()==best.duration()&&candidate.cost()<best.cost()))best=candidate;
            }
            return best;
        }
        double yaw=Math.toDegrees(Math.atan2(s.x()-d.x(),d.z()-s.z()));
        double[] turns=d.emergency()?new double[]{-90,90,-135,135,180,0}:d.departure()?new double[]{0,-90,90,180}:new double[]{0};
        double[] pitches=d.continuation()?new double[]{0}:d.emergency()?new double[]{-35}:d.departure()?new double[]{-35,-55,-70}:new double[]{-15,-35,-55};
        for(int duration=1;duration<=maximumTicks(strength);duration++){
            Plan best=null;
            for(double turn:turns)for(double pitch:pitches){
                var p=new Plan(yaw+turn,pitch,d.height(),d.x(),d.z(),strength,duration,d.departure(),d.minimumSpeed(),0,List.of(),d);
                var result=certify(d.departure()?aimLaunch(s,p.yaw(),p.pitch()):s,p,WINDUP,gravity,space,water);
                if(result!=null&&(best==null||result.cost()<best.cost()))best=result;
            }
            if(best!=null)return best;
        }
        return null;
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water) {
        return choose(s,x,height,z,strength,gravity,space,water,0);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water,int elapsed) {
        return choose(s,x,height,z,strength,gravity,space,water,elapsed,.55);
    }
    public static Plan choose(FlightPrediction.State s,double x,double height,double z,float strength,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water,int elapsed,double minimumSpeed) {
        if(!(strength>0)||!Float.isFinite(strength))return null;
        boolean departure=elapsed==WINDUP;
        double yaw=Math.toDegrees(Math.atan2(s.x()-x,z-s.z()));
        // Every tick length is available, not a sparse set of duration presets. Stop at the shortest feasible duration.
        for(int duration=1;duration<=maximumTicks(strength);duration++) {
            Plan best=null;
            for(double turn:departure?new double[]{0,-90,90,180}:new double[]{0})
            for(double pitch:departure?new double[]{-35,-55,-70}:new double[]{-15,-35,-55}) {
                var start=departure?aimLaunch(s,yaw+turn,pitch):s;
                var p=certify(start,new Plan(yaw+turn,pitch,height,x,z,strength,duration,departure,minimumSpeed,0,List.of()),elapsed,gravity,space,water);
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
        int horizon=p.demand()!=null&&p.demand().continuation()?p.end():p.handoff();
        for(int tick=elapsed;tick<horizon;tick++) {
            var remaining=p.demand()==null?p:p.withDemand(p.demand().after(tick-elapsed));
            var next=step(s,remaining,tick,gravity,water.wet(s));
            if(!space.clear(s,next,.2,.15))return null;
            // Leave room for a fresh spear windup / landing turn instead of overshooting the destination.
            if(!p.departure()&&(p.demand()==null||!p.demand().continuation()&&!p.demand().emergency())&&(p.goalX()-next.x())*dx+(p.goalZ()-next.z())*dz<6)return null;
            cost+=Math.pow(Math.max(0,p.height()-next.y()),2)*.01;
            s=next;path.add(s);
            // A departure must already have usable height AND speed when thrust stops.
            // Recovery cannot borrow an optimistic future climb to certify a weak hop.
            if(p.departure()&&tick==p.end()-1&&!departureReady(s,p))return null;
        }
        if(p.demand()!=null&&p.demand().continuation()){
            var d=p.demand().after(Math.max(0,p.end()-elapsed));
            SweepingAttackPlanner.Contact contact=(state,aim,tick)->space.clear(state,
                new FlightPrediction.State(aim.x()+aim.vx()*tick,aim.bottom()+aim.height()*.5+aim.vy()*tick,aim.z()+aim.vz()*tick,0,0,0,0,0),0,0);
            var tail=SweepingAttackPlanner.simulate(s,d.path(),d.aim(),0,gravity,d.rate(),d.minimumSpeed(),d.exiting(),space,contact,120);
            if(!tail.safe()||!tail.hit()||!d.exiting()&&tail.contactTick()>d.contactDeadline())return null;
            for(int i=1;i<tail.trace().size();i++)path.add(tail.trace().get(i).state());
            return new Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),p.duration(),p.departure(),p.minimumSpeed(),tail.cost(),path,p.demand());
        }
        if(water.wet(s)||Math.hypot(s.vx(),s.vz())<p.minimumSpeed()||s.y()<p.height()-1||p.departure()&&s.vy()<-.5)return null;
        if(p.departure()&&(!departureReady(s,p)||!turnReserve(s,p,gravity,space,water)))return null;
        return new Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),p.duration(),p.departure(),p.minimumSpeed(),cost+Math.abs(s.y()-p.height())*.02,List.copyOf(path),p.demand());
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
