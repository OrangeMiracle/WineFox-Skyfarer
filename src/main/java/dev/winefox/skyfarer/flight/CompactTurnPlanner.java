package dev.winefox.skyfarer.flight;

/** Trade kinetic energy for height while turning toward a fixed destination. Never extend an exit. */
public final class CompactTurnPlanner {
    private CompactTurnPlanner() {}
    public record Goal(double x,double y,double z,double originX,double originZ,double radius,double nextX,double nextZ) {
        public Goal(double x,double y,double z,double originX,double originZ,double radius) {this(x,y,z,originX,originZ,radius,Double.NaN,Double.NaN);}
        public boolean staging(){return Double.isFinite(nextX);}
        public boolean readyInward(double px,double pz){return staging() && (Math.hypot(px-x,pz-z)<5 || Math.hypot(px-nextX,pz-nextZ)>=Math.hypot(x-nextX,z-nextZ)*.85);}
        public Goal inward(){return new Goal(nextX,y,nextZ,originX,originZ,Math.max(radius,48));}
    }
    public record Command(double yaw,double pitch,double yawRate,double pitchRate) {}
    public record Plan(Command command,double cost,double excursion,double speed,double brake) {}
    public static double angle(FlightPrediction.State s,double x,double z) {
        double desired=Math.toDegrees(Math.atan2(s.x()-x,z-s.z()));
        double momentum=Math.hypot(s.vx(),s.vz())>.08?Math.toDegrees(Math.atan2(-s.vx(),s.vz())):s.yaw();
        return Math.max(Math.abs(FlightPrediction.turn(momentum,desired,180)-momentum),
            Math.abs(FlightPrediction.turn(s.yaw(),desired,180)-s.yaw()));
    }
    public static Command command(FlightPrediction.State s,Goal g,double gravity,boolean rocket,double rate,double brake) {
        double yaw=Math.toDegrees(Math.atan2(s.x()-g.x,g.z-s.z()));
        double correction=FlightPrediction.turn(s.yaw(),yaw,180)-s.yaw();
        // Soften the final small correction by 15%, retaining large-turn authority and radius.
        if(Math.abs(correction)<12) yaw=s.yaw()+correction*.85;
        double speed=Math.hypot(s.vx(),s.vz()),angle=angle(s,g.x,g.z);
        double pitch=GlidePlanner.feedbackPitch(s,g.y,0,gravity,rocket);
        // Convert momentum only into height still needed by the fixed goal. Burning fuel alone
        // must not pin the nose up after the goal is reached (an endless climbing spiral).
        if(angle>35 && speed>.55 && s.y()+Math.max(0,s.vy())*10<g.y) pitch=Math.min(pitch,brake);
        // Do not demand potential energy which a slow, unpowered glide does not possess.
        if(!rocket && speed<.65) pitch=Math.max(pitch,-6);
        return new Command(yaw,pitch,rocket||speed<.8?Math.max(8,rate):rate,8);
    }
    public static Plan choose(FlightPrediction.State start,Goal g,int rocketTicks,double gravity,double rate,GlidePlanner.Corridor space) {
        Plan best=null;
        for(double brake:new double[]{-65,-55,-40,-25}) {
            var s=start; double cost=0,excursion=0; Command first=null; Goal active=g;
            int baseline=Math.max(32,rocketTicks+16);
            if(baseline>96) return null;
            int horizon=PredictionHorizon.ticks(baseline);
            boolean safe=true;
            for(int tick=0;tick<horizon;tick++) {
                if(active.readyInward(s.x(),s.z())) active=active.inward();
                var c=command(s,active,gravity,tick<rocketTicks,rate,brake);
                if(first==null) first=c;
                var next=FlightPrediction.step(s,c.yaw,c.pitch,c.yawRate,c.pitchRate,c.pitchRate,gravity,tick<rocketTicks);
                double away=Math.hypot(next.x()-g.originX,next.z()-g.originZ);
                if(away>active.radius || !space.clear(s,next,.04,.04)) {
                    if(tick<baseline)safe=false;else cost+=PredictionHorizon.futureRisk(tick,baseline);
                    break;
                }
                excursion=Math.max(excursion,away);
                cost+=(angle(next,active.x,active.z)*.03+Math.abs(next.y()-g.y)*.04+away*.015)*PredictionHorizon.weight(tick,baseline);
                s=next;
            }
            if(!safe) continue;
            cost+=excursion*.5+angle(s,active.x,active.z)*.3;
            if(best==null || cost<best.cost) best=new Plan(first,cost,excursion,Math.hypot(s.vx(),s.vz()),brake);
        }
        return best;
    }
    public static java.util.List<GlidePlanner.TracePoint> trace(FlightPrediction.State start,Goal goal,Plan plan,int rocket,double gravity,double rate,GlidePlanner.Corridor space) {
        var path=new java.util.ArrayList<GlidePlanner.TracePoint>();var state=start;var active=goal;
        path.add(new GlidePlanner.TracePoint(state,.04,.04));
        for(int tick=0;tick<PredictionHorizon.ticks(Math.max(32,rocket+16));tick++) {
            if(active.readyInward(state.x(),state.z()))active=active.inward();
            var c=command(state,active,gravity,tick<rocket,rate,plan.brake);
            var next=FlightPrediction.step(state,c.yaw,c.pitch,c.yawRate,c.pitchRate,c.pitchRate,gravity,tick<rocket);
            path.add(new GlidePlanner.TracePoint(next,.04,.04));
            if(!space.clear(state,next,.04,.04))break;state=next;
        }return path;
    }
    public static double pulseCost(FlightPrediction.State s,Goal goal,int duration,double gravity,double rate,GlidePlanner.Corridor space) {
        double cost=0;
        for(int ticks=RocketProfile.minimumTicks(duration);ticks<=RocketProfile.maximumTicks(duration);ticks++) {
            var p=choose(s,goal,ticks,gravity,rate,space);
            if(p==null) return Double.POSITIVE_INFINITY;
            cost+=p.cost;
        }
        return cost/12+RocketProfile.expectedTicks(duration)*.4;
    }
}
