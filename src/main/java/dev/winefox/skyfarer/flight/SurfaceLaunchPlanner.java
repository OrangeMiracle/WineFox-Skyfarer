package dev.winefox.skyfarer.flight;

/** A finite rocket pulse to leave a surface. Predictions never move the real entity. */
public final class SurfaceLaunchPlanner {
    private SurfaceLaunchPlanner() {}
    public static final int DEPARTURE_TICKS = 6;
    public record Plan(double yaw, double pitch, double height, double cost) {}
    public static Plan chooseCoast(FlightPrediction.State start,double yaw,double height,double gravity,GlidePlanner.Corridor space,Water water) {
        Plan best=null;
        for(double pitch:new double[]{-25,0,15}) {
            var plan=new Plan(yaw,pitch,height,0);double cost=forecast(start,plan,gravity,0,space,water);
            if(Double.isFinite(cost)&&(best==null||cost<best.cost()))best=new Plan(yaw,pitch,height,cost);
        }
        return best;
    }
    @FunctionalInterface public interface Water { boolean wet(FlightPrediction.State state); }
    public static final Water DRY = state -> false;

    public static double command(FlightPrediction.State s, Plan p, int elapsed, double gravity, boolean rocket) {
        return elapsed < DEPARTURE_TICKS ? p.pitch : GlidePlanner.feedbackPitch(s,p.height,0,gravity,rocket);
    }
    public static FlightPrediction.State step(FlightPrediction.State s,double yaw,double pitch,double gravity,boolean rocket,boolean wet) {
        if (!wet) return FlightPrediction.step(s,yaw,pitch,180,90,90,gravity,rocket);
        // EntityMaid.travel with wantToSwim: move by current velocity, then apply 0.9 drag.
        double vx=s.vx()*.9,vy=s.vy()*.9,vz=s.vz()*.9;
        if(rocket && s.rocketDelay()==0) {
            var look=VanillaElytra.look(yaw,pitch);
            vx+=look.x()*.1+(look.x()*1.5-vx)*.5;
            vy+=look.y()*.1+(look.y()*1.5-vy)*.5;
            vz+=look.z()*.1+(look.z()*1.5-vz)*.5;
        }
        return new FlightPrediction.State(s.x()+s.vx(),s.y()+s.vy(),s.z()+s.vz(),vx,vy,vz,yaw,pitch,s.physics()==null?null:s.physics().nextTick(),Math.max(0,s.rocketDelay()-1));
    }
    /** One attitude must work for every possible lifetime of the selected inventory rocket. */
    public static Plan choose(FlightPrediction.State start,double yaw,double height,double gravity,int duration,
                              GlidePlanner.Corridor space,Water water) {
        return choose(start.withRocketDelay(1),yaw,height,gravity,RocketProfile.minimumTicks(duration),RocketProfile.maximumTicks(duration),space,water);
    }
    public static Plan chooseRemaining(FlightPrediction.State start,double yaw,double height,double gravity,int ticks,
                                       GlidePlanner.Corridor space,Water water) {
        return choose(start,yaw,height,gravity,ticks,ticks,space,water);
    }
    private static Plan choose(FlightPrediction.State start,double yaw,double height,double gravity,int minimum,int maximum,
                               GlidePlanner.Corridor space,Water water) {
        if(maximum>64) return null;
        Plan best=null;
        for(double turn:new double[]{0,-45,45,-90,90,180}) for(double pitch:new double[]{-88,-70,-50,-25,0,15}) {
            Plan candidate=new Plan(yaw+turn,pitch,height,0);
            double worst=0; boolean safe=true;
            for(int life=minimum;life<=maximum;life++) {
                double cost=forecast(start,candidate,gravity,life,space,water);
                if(!Double.isFinite(cost)) { safe=false; break; }
                worst=Math.max(worst,cost);
            }
            if(!safe) continue;
            double cost=worst+Math.abs(turn)*.025+Math.abs(pitch)*.008;
            if(best==null || cost<best.cost) best=new Plan(candidate.yaw,pitch,height,cost);
        }
        return best;
    }
    private static double forecast(FlightPrediction.State start,Plan plan,double gravity,int life,
                                   GlidePlanner.Corridor space,Water water) {
        var s=start; double earlyRise=0,cost=0;
        for(int tick=0;tick<PredictionHorizon.ticks(life+start.rocketDelay()+16);tick++) {
            double pitch=command(s,plan,tick,gravity,RocketProfile.active(tick,life,start));
            var next=step(s,plan.yaw,pitch,gravity,RocketProfile.active(tick,life,start),water.wet(s));
            // Small physical clearance is essential over snow layers and during the first jump.
            if(!space.clear(s,next,.04,.01)) {
                if(tick<life+start.rocketDelay()+16)return Double.POSITIVE_INFINITY;
                cost+=PredictionHorizon.futureRisk(tick,life+start.rocketDelay()+16);break;
            }
            s=next;
            if(tick==DEPARTURE_TICKS-1) earlyRise=s.y()-start.y();
            cost+=Math.pow(Math.max(0,plan.height-s.y()),2)*.015*PredictionHorizon.weight(tick,life+start.rocketDelay()+16);
        }
        if(water.wet(s)) return Double.POSITIVE_INFINITY;
        return cost+Math.pow(Math.max(0,2-earlyRise),2)*12;
    }
    public static double pulseCost(FlightPrediction.State start,Plan plan,double gravity,int duration,
                                   GlidePlanner.Corridor space,Water water) {
        if(RocketProfile.maximumTicks(duration)>64) return Double.POSITIVE_INFINITY;
        double worst=0;
        for(int life=RocketProfile.minimumTicks(duration);life<=RocketProfile.maximumTicks(duration);life++) {
            double score=forecast(start.withRocketDelay(1),plan,gravity,life,space,water);
            if(!Double.isFinite(score)) return Double.POSITIVE_INFINITY;
            worst=Math.max(worst,score);
        }
        return worst+RocketProfile.expectedTicks(duration)*.1;
    }
    public static java.util.List<GlidePlanner.TracePoint> trace(FlightPrediction.State start,Plan plan,int elapsed,
            double gravity,int remaining,GlidePlanner.Corridor space,Water water) {
        var path=new java.util.ArrayList<GlidePlanner.TracePoint>();var s=start;
        path.add(new GlidePlanner.TracePoint(s,.04,.01));
        for(int tick=0;tick<PredictionHorizon.ticks(Math.min(64,remaining+start.rocketDelay()+16));tick++) {
            double pitch=command(s,plan,elapsed+tick,gravity,RocketProfile.active(tick,remaining,start));
            var next=step(s,plan.yaw,pitch,gravity,RocketProfile.active(tick,remaining,start),water.wet(s));
            path.add(new GlidePlanner.TracePoint(next,.04,.01));
            if(!space.clear(s,next,.04,.01))break;s=next;
        }
        return java.util.List.copyOf(path);
    }
}
