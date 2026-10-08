package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** Bounded last-resort escape search. Every accepted command includes the complete pulse and coast. */
public final class EmergencyAvoidancePlanner {
    private EmergencyAvoidancePlanner() {}
    private static final double[][] ATTITUDES={{90,-20},{-90,-20},{135,-35},{-135,-35},{180,-20},{0,-75}};
    public record Holding(double yaw,double pitch,int clearTicks) {}
    /** Receding, 12-tick live steering guard while the full worker certificate is unavailable.
     * Never authorizes ignition. One retained attitude plus at most six alternatives,
     * no geometry search or recovery claim. */
    public static Holding holding(FlightPrediction.State start,int active,double gravity,GlidePlanner.Corridor live,Holding previous){
        if(previous!=null){
            var checked=holdingCheck(start,previous.yaw(),previous.pitch(),active,gravity,live);
            if(checked.clearTicks()==12)return checked;
        }
        double yaw=Math.hypot(start.vx(),start.vz())>.1?Math.toDegrees(Math.atan2(-start.vx(),start.vz())):start.yaw();
        Holding best=null;
        for(var attitude:ATTITUDES){
            var candidate=holdingCheck(start,yaw+attitude[0],attitude[1],active,gravity,live);
            if(best==null||candidate.clearTicks()>best.clearTicks())best=candidate;
            if(candidate.clearTicks()==12)return candidate;
        }
        return best;
    }
    private static Holding holdingCheck(FlightPrediction.State start,double yaw,double pitch,int active,double gravity,GlidePlanner.Corridor live){
        var state=start;
        for(int tick=0;tick<12;tick++){
            var next=FlightPrediction.step(state,yaw,pitch,45,20,20,gravity,RocketProfile.active(tick,active,start));
            if(!live.clear(state,next,.15,.12))return new Holding(yaw,pitch,tick);
            state=next;
        }
        return new Holding(yaw,pitch,12);
    }
    public record Danger(boolean imminent,int ttc,int margin,double divergence) {}
    public record Plan(double yaw,double pitch,double height,int duration,int ticks,List<GlidePlanner.TracePoint> trace) {
        public String mode(){return duration<0?"EMERGENCY_GLIDE_TURN":"EMERGENCY_BOOST_TURN";}
        /** Execute the initial escape attitude, then let normal recovery plan from live momentum.
         * The longer certificate still covers the complete attached pulse and coast. */
        public int controlTicks(){return Math.min(8,ticks);}
    }
    public static int margin(FlightPrediction.State s,double desiredYaw,double rate){
        double speed=Math.sqrt(s.vx()*s.vx()+s.vy()*s.vy()+s.vz()*s.vz());
        double velocityYaw=Math.toDegrees(Math.atan2(-s.vx(),s.vz()));
        double divergence=Math.abs(FlightPrediction.turn(velocityYaw,desiredYaw,180)-velocityYaw);
        return Math.clamp((int)Math.ceil(4+speed*3+divergence/Math.max(3,rate)),6,32);
    }
    /** Predict actual momentum while turning, rather than casting a ray along the desired look. */
    public static Danger danger(FlightPrediction.State start,double yaw,double pitch,double rate,int rocket,double gravity,GlidePlanner.Corridor space){
        int margin=margin(start,yaw,rate);var s=start;
        double divergence=Math.abs(FlightPrediction.turn(Math.toDegrees(Math.atan2(-s.vx(),s.vz())),yaw,180)-Math.toDegrees(Math.atan2(-s.vx(),s.vz())));
        for(int tick=0;tick<margin;tick++){
            var next=FlightPrediction.step(s,yaw,pitch,rate,9,14,gravity,RocketProfile.active(tick,rocket,start));
            if(!space.clear(s,next,.15,.12))return new Danger(true,tick+1,margin,divergence);
            s=next;
        }
        return new Danger(false,Integer.MAX_VALUE,margin,divergence);
    }
    /** A committed curve includes its changing pitch and Strike -> Exit transition. Do not replace
     * that control law with a constant nose attitude when testing the stopping/turning margin. */
    public static Danger dangerOnPath(FlightPrediction.State start,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,boolean exiting,
                                      double rate,int rocket,double gravity,double minimum,GlidePlanner.Corridor space){
        var first=SweepingAttackPlanner.command(start,path,aim,exiting,rocket,gravity,rate);
        int margin=margin(start,first.yaw(),rate);var s=start;boolean hit=exiting;
        for(int tick=0;tick<margin;tick++){
            var c=SweepingAttackPlanner.command(s,path,aim==null?null:aim.after(tick),hit,RocketProfile.remaining(tick,rocket,start),gravity,rate);
            var look=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),c.yaw(),c.yawRate()),FlightPrediction.turn(s.pitch(),c.pitch(),c.pitchRate()));
            if(!hit&&aim!=null&&tick>=aim.readyIn()&&Math.hypot(s.vx(),s.vz())>=minimum&&aim.missSquared(look,tick)<=1e-8)hit=true;
            var next=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),gravity,RocketProfile.active(tick,rocket,start));
            if(!space.clear(s,next,.15,.12))return new Danger(true,tick+1,margin,0);
            s=next;
        }
        return new Danger(false,Integer.MAX_VALUE,margin,0);
    }
    public static double pitch(FlightPrediction.State s,Plan plan,int elapsed,double gravity,int rocket){
        return elapsed<8?plan.pitch:GlidePlanner.feedbackPitch(s,plan.height,0,gravity,rocket>0);
    }
    public static Plan choose(FlightPrediction.State start,int active,List<Integer> durations,double gravity,GlidePlanner.Corridor space){
        double velocityYaw=Math.hypot(start.vx(),start.vz())>.1?Math.toDegrees(Math.atan2(-start.vx(),start.vz())):start.yaw();
        // Six escape attitudes; glide always gets the first opportunity, including any active rocket.
        for(var c:ATTITUDES){
            var plan=certify(start,velocityYaw+c[0],c[1],-1,active,gravity,space);
            if(plan!=null)return plan;
        }
        if(active>0)return null; // Never stack a pulse or pretend an existing one can be stopped.
        for(int duration:durations.stream().distinct().limit(3).toList())for(var c:ATTITUDES){
            var plan=certify(start,velocityYaw+c[0],c[1],duration,0,gravity,space);
            if(plan!=null)return plan;
        }
        return null;
    }
    public static Plan certify(FlightPrediction.State start,double yaw,double pitch,int duration,int active,double gravity,GlidePlanner.Corridor space){
        int min=duration<0?active:RocketProfile.minimumTicks(duration),max=duration<0?active:RocketProfile.maximumTicks(duration);
        if(max>64)return null;
        var initial=duration<0?start:start.withRocketDelay(1);
        int ticks=Math.max(36,max+initial.rocketDelay()+20);
        var plan=new Plan(yaw,pitch,start.y()+6,duration,ticks,List.of());
        List<GlidePlanner.TracePoint> trace=List.of();
        for(int life=min;life<=max;life++){
            var s=initial;var points=new ArrayList<GlidePlanner.TracePoint>();points.add(new GlidePlanner.TracePoint(s,.15,.12));
            for(int tick=0;tick<ticks;tick++){
                double command=pitch(s,plan,tick,gravity,RocketProfile.remaining(tick,life,initial));
                var next=FlightPrediction.step(s,yaw,command,45,20,20,gravity,RocketProfile.active(tick,life,initial));
                if(!space.clear(s,next,.15,.12))return null;
                s=next;points.add(new GlidePlanner.TracePoint(s,.15,.12));
            }
            trace=List.copyOf(points);
        }
        return new Plan(yaw,pitch,plan.height,duration,ticks,trace);
    }
}
