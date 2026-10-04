package dev.winefox.skyfarer.flight;

/** Receding-horizon, finite-pulse glide planning. Geometry is hard; smoothness and energy are soft. */
public final class GlidePlanner {
    private GlidePlanner() {}
    @FunctionalInterface public interface Corridor {
        boolean clear(FlightPrediction.State from, FlightPrediction.State to, double side, double vertical);
    }
    public enum Mode { GLIDE, RECOVERY, TURN, PRECISION, AIRBRAKE, REVERSAL, NO_CLEAR_PATH }
    public record Exit(double x,double y,double z,double gradientX,double gradientZ) {
        public Exit(double x,double y,double z) { this(x,y,z,Double.NaN,Double.NaN); }
        public double supportHeight(FlightPrediction.State s) {
            return Double.isFinite(gradientX)?y+gradientX*(s.x()-x)+gradientZ*(s.z()-z):y;
        }
    }
    public record Request(double yaw, double height, double distance, double slope, double flare, double floor,
        int rocketTicks, double yawRate, double pitchRate, double recoveryRate, double gravity,
        double sideMargin, double verticalMargin, boolean recovery, FlightPrediction.Aim aim, Exit exit) {
        public Request(double yaw,double height,double distance,double slope,double flare,double floor,int rocketTicks,
            double yawRate,double pitchRate,double recoveryRate,double gravity,double sideMargin,double verticalMargin,
            boolean recovery,FlightPrediction.Aim aim) {
            this(yaw,height,distance,slope,flare,floor,rocketTicks,yawRate,pitchRate,recoveryRate,gravity,sideMargin,verticalMargin,recovery,aim,null);
        }
        public Request withPulse(int ticks) { return new Request(yaw,height,distance,slope,flare,floor,ticks,
            yawRate,pitchRate,recoveryRate,gravity,sideMargin,verticalMargin,recovery,aim,exit); }
        public Request withExit(Exit destination) {
            if(flare==-1 && !Double.isFinite(destination.gradientX)) destination=new Exit(destination.x,destination.y,destination.z,
                slope*Math.sin(Math.toRadians(yaw)),-slope*Math.cos(Math.toRadians(yaw)));
            return new Request(yaw,height,distance,slope,flare,floor,rocketTicks,
                yawRate,pitchRate,recoveryRate,gravity,sideMargin,verticalMargin,recovery,aim,destination);
        }
    }
    public record Plan(double yaw, double pitch, double yawRate, double pitchRate, Mode mode, boolean safe,
        double cost, double reserve, int clearTicks, double heightOffset) {}
    public static double energy(FlightPrediction.State s, double referenceY, double gravity) {
        return gravity*(s.y()-referenceY) + 0.5*(s.vx()*s.vx()+s.vy()*s.vy()+s.vz()*s.vz());
    }
    /** Vanilla wall damage is based on horizontal speed lost, not distance to a mountain. */
    public static double wallDamage(double before,double after) { return Math.max(0,(before-after)*10-3); }
    private static boolean exitDue(Request r,double remaining) { return remaining < -Math.max(2,r.aim==null?2.25:r.aim.maxReach()); }
    private static Request exitRequest(FlightPrediction.State s,Request r,int pulse) {
        double yaw=Math.toDegrees(Math.atan2(s.x()-r.exit.x,r.exit.z-s.z()));
        return new Request(yaw,r.exit.y,0,0,14,Double.NaN,pulse,Math.max(5,r.yawRate),r.pitchRate,
            Math.max(8,r.recoveryRate),r.gravity,r.sideMargin,r.verticalMargin,true,null,r.exit);
    }
    public static Plan choose(FlightPrediction.State initial, Request r, Corridor corridor) {
        double nominal = FlightPrediction.choose(initial,r.yaw,r.height,r.distance,r.slope,r.flare,
            Double.NaN,r.rocketTicks,r.yawRate,r.pitchRate,r.recoveryRate,r.gravity,r.aim).pitch();
        Plan best = null, survival = null;
        // Safe gradual control always wins over emergency maneuvers, even if an emergency is cheaper.
        for (double side : new double[]{r.sideMargin, 0.04}) {
            double vertical = side == r.sideMargin ? r.verticalMargin : 0.04;
            for (double pitch : new double[]{nominal, nominal-8, nominal+8, -55,-35,-18,-6,0,15,30}) {
                Mode mode = pitch < -30 || r.recovery ? Mode.RECOVERY : Mode.GLIDE;
                Plan p = evaluate(initial,r,corridor,r.yaw,Math.clamp(pitch,r.flare<0?-75:-65,r.flare<0?80:50),mode,side,vertical,0);
                if(r.flare<0 && r.aim!=null) p=trackingCost(p,initial,nominal);
                survival = survivesLonger(survival,p);
                if (p.safe && (best==null || p.cost<best.cost)) best=p;
            }
            // Keep attack heading whenever any vertical correction can preserve its corridor.
            if (best!=null) return best;
        }
        // A slope or low roof may need a different corridor height. Check complete trajectories;
        // never raise the whole attack because a distant ground/roof ray found a high block.
        for (double offset : new double[]{0.75,1.5,3,5,-0.75,-1.5,-3}) {
            for (double pitch : new double[]{nominal,offset>0?-35:15,offset>0?-55:30}) {
                Plan p=evaluate(initial,r,corridor,r.yaw,pitch,Mode.RECOVERY,r.sideMargin,r.verticalMargin,offset);
                survival=survivesLonger(survival,p);
                if (p.safe && (best==null || p.cost<best.cost)) best=p;
            }
        }
        if (best!=null) return best;
        for (double side : new double[]{r.sideMargin, 0.04}) {
            double vertical = side == r.sideMargin ? r.verticalMargin : 0.04;
            for (double offset : new double[]{-25,25,-50,50}) {
                Plan p = evaluate(initial,r,corridor,r.yaw+offset,nominal,Mode.TURN,side,vertical,0);
                survival=survivesLonger(survival,p);
                if (p.safe && (best==null || p.cost<best.cost)) best=p;
            }
            if (best!=null) return best;
        }
        // Low impact energy permits faster, bounded corrections before a 90-degree pitch brake.
        // All candidates must still clear the complete body sweep for the full horizon.
        if(r.rocketTicks==0 && wallDamage(Math.hypot(initial.vx(),initial.vz()),0)<=4) {
            for(double offset:new double[]{0,-35,35,-70,70,-90,90,-120,120}) for(double pitch:new double[]{nominal,-35,-60}) {
                Plan p=evaluate(initial,r,corridor,r.yaw+offset,pitch,Mode.PRECISION,.04,.04,0);
                survival=survivesLonger(survival,p);
                if(p.safe && (best==null || p.cost<best.cost)) best=p;
            }
            if(best!=null) return best;
        }
        // Pitch brake is considered before reversal and is rejected if a ceiling makes it unsafe.
        Plan brake=evaluate(initial,r,corridor,initial.yaw(),-88,Mode.AIRBRAKE,0.04,0.04,0);
        if (brake.safe) return brake;
        survival=survivesLonger(survival,brake);
        for (double pitch : new double[]{0,-20}) {
            Plan reverse=evaluate(initial,r,corridor,initial.yaw()+180,pitch,Mode.REVERSAL,0.04,0.04,0);
            if (reverse.safe && (best==null || reverse.cost<best.cost)) best=reverse;
            survival=survivesLonger(survival,reverse);
        }
        if (best!=null) return best;
        return new Plan(survival.yaw,survival.pitch,survival.yawRate,survival.pitchRate,Mode.NO_CLEAR_PATH,
            false,Double.POSITIVE_INFINITY,survival.reserve,survival.clearTicks,survival.heightOffset);
    }
    private static Plan survivesLonger(Plan a, Plan b) { return a==null || b.clearTicks>a.clearTicks ? b : a; }
    private static Plan trackingCost(Plan p,FlightPrediction.State s,double nominal) {
        double desired=FlightPrediction.turn(s.pitch(),nominal,p.pitchRate());
        double actual=FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate());
        return new Plan(p.yaw,p.pitch,p.yawRate,p.pitchRate,p.mode,p.safe,p.cost+Math.pow(actual-desired,2)*10,
            p.reserve,p.clearTicks,p.heightOffset);
    }
    private static Plan evaluate(FlightPrediction.State initial, Request r, Corridor corridor, double yaw, double pitch,
        Mode mode, double side, double vertical, double heightOffset) {
        boolean emergency=mode==Mode.AIRBRAKE || mode==Mode.REVERSAL;
        boolean precise=mode==Mode.PRECISION;
        double yawRate=mode==Mode.REVERSAL?180:precise?Math.max(12,r.yawRate):r.yawRate;
        double pitchRate=emergency?90:precise?Math.max(12,r.pitchRate):r.pitchRate;
        double firstRate=emergency?90:precise?pitchRate:pitch<initial.pitch()?r.recoveryRate:pitchRate;
        var s=initial; double cost=switch(mode) {case GLIDE->1;case RECOVERY->r.flare<0?1:4;case TURN->10;case PRECISION->14;case AIRBRAKE->30;default->50;};
        double dx=-Math.sin(Math.toRadians(r.yaw)), dz=Math.cos(Math.toRadians(r.yaw));
        double reserve=Double.POSITIVE_INFINITY, bestMiss=Double.POSITIVE_INFINITY;
        int horizon=PredictionHorizon.ticks(24);
        int clearTicks=horizon;
        double slopeCommand=pitch;
        Request active=r;
        for (int tick=0;tick<horizon;tick++) {
            double along=(s.x()-initial.x())*dx+(s.z()-initial.z())*dz;
            double remaining=r.distance-along;
            if(active.exit!=null && !active.recovery && exitDue(r,remaining)) active=exitRequest(s,active,Math.max(0,r.rocketTicks-tick));
            boolean exiting=active!=r || active.recovery&&active.exit!=null;
            double goal=FlightPrediction.pathHeight(exiting?0:remaining,active.height,active.slope,active.flare)+heightOffset;
            if(exiting) goal=Math.max(goal,active.exit.supportHeight(s)+heightOffset);
            // Roll out feedback after the initial maneuver instead of assuming a frozen pitch.
            double goalVelocity=FlightPrediction.pathVerticalSpeed(s,exiting?0:remaining,active.slope,active.flare);
            if(exiting && Double.isFinite(active.exit.gradientX) && active.exit.supportHeight(s)>=active.height)
                goalVelocity=Math.max(0,active.exit.gradientX*s.vx()+active.exit.gradientZ*s.vz());
            double command=tick<(emergency?4:2)?pitch:feedbackPitch(s,goal,goalVelocity,r.gravity,tick<r.rocketTicks);
            if(active.flare<0 && !emergency && tick>=2) {
                if((tick-2)%4==0) slopeCommand=FlightPrediction.choose(s,r.yaw,r.height+heightOffset,remaining,r.slope,r.flare,Double.NaN,
                    Math.max(0,r.rocketTicks-tick),r.yawRate,r.pitchRate,r.recoveryRate,r.gravity).pitch();
                command=slopeCommand;
            }
            double commandYaw=exiting?active.yaw:emergency||precise?yaw:tick<6?yaw:r.yaw;
            var next=FlightPrediction.step(s,commandYaw,command,exiting?active.yawRate:yawRate,pitchRate,
                emergency?90:precise?pitchRate:active.recoveryRate,r.gravity,tick<r.rocketTicks);
            if (!corridor.clear(s,next,side,vertical)) {
                if(tick<24)return new Plan(yaw,pitch,yawRate,firstRate,mode,false,Double.POSITIVE_INFINITY,reserve,tick,heightOffset);
                // Beyond the committed control window, the next pulse and maneuver are unknown.
                // Still sample real geometry and penalize this candidate, without banning takeoff
                // because an indefinitely unpowered continuation eventually reaches the ground.
                cost+=PredictionHorizon.futureRisk(tick,24);clearTicks=tick;break;
            }
            double weight=PredictionHorizon.weight(tick,24);
            double bandError=Math.max(0,Math.abs(next.y()-goal)-0.65);
            double verticalError=s.vy()-(r.flare<0?goalVelocity:0);
            cost+=(bandError*bandError*0.8+verticalError*verticalError*2)*weight;
            // Energy headroom above the next corridor, including the velocity that remains after drag.
            double available=energy(next,goal,r.gravity)-0.5*0.65*0.65;
            if(tick<24)reserve=Math.min(reserve,available);
            cost+=Math.pow(Math.min(0,available),2)*35*weight;
            double lateral=(next.x()-initial.x())*dz-(next.z()-initial.z())*dx;
            if(!exiting) cost+=lateral*lateral*0.06*weight;
            if (r.aim!=null && tick>=r.aim.readyIn()) bestMiss=Math.min(bestMiss,r.aim.missSquared(next,tick+1));
            s=next;
        }
        if (r.aim!=null && Double.isFinite(bestMiss)) cost+=Math.min(bestMiss,16)*(r.distance<8?80:5);
        cost+=0.02875*Math.pow(FlightPrediction.turn(initial.pitch(),pitch,firstRate)-initial.pitch(),2);
        return new Plan(yaw,pitch,yawRate,firstRate,mode,true,cost+Math.abs(heightOffset)*6,reserve,clearTicks,heightOffset);
    }
    public static boolean ordinary(Plan p) {
        return p.safe && p.mode!=Mode.AIRBRAKE && p.mode!=Mode.REVERSAL;
    }
    public record TracePoint(FlightPrediction.State state,double side,double vertical) {}
    /** Re-run only the selected candidate through the same predictor used to certify its volume. */
    public static java.util.List<TracePoint> trace(FlightPrediction.State start,Request request,Plan plan,Corridor space) {
        var path=new java.util.ArrayList<TracePoint>();
        for(double side:new double[]{request.sideMargin,.04}) {
            path.clear();double vertical=side==request.sideMargin?request.verticalMargin:.04;
            path.add(new TracePoint(start,side,vertical));
            var result=evaluate(start,request,(a,b,s,v)->{path.add(new TracePoint(b,s,v));return space.clear(a,b,s,v);},
                plan.yaw,plan.pitch,plan.mode,side,vertical,plan.heightOffset);
            if(result.safe || !plan.safe)break;
        }
        return java.util.List.copyOf(path);
    }
    public record Forecast(boolean safe, double reserve, boolean passed, double miss) {
        public Forecast(boolean safe,double reserve) { this(safe,reserve,false,Double.POSITIVE_INFINITY); }
    }
    public static double pulseCost(FlightPrediction.State start,Request request,Corridor corridor,int duration,double minimumSpeed,double reserve) {
        double cost=0;
        for (int ticks=RocketProfile.minimumTicks(duration);ticks<=RocketProfile.maximumTicks(duration);ticks++) {
            var candidate=request.withPulse(ticks);
            var plan=choose(start,candidate,corridor);
            if (!ordinary(plan)) return Double.POSITIVE_INFINITY;
            var future=forecast(start,candidate,plan,corridor,minimumSpeed);
            if (!future.safe()) return Double.POSITIVE_INFINITY;
            // Reject a pulse which carries the maid past contact before the native windup/aim is ready.
            if(request.exit!=null && request.aim!=null && future.passed() && future.miss()>.04) return Double.POSITIVE_INFINITY;
            cost+=Math.pow(Math.max(0,reserve-future.reserve()),2)*200+Math.max(0,future.reserve()-reserve)*2;
        }
        return cost/12+RocketProfile.expectedTicks(duration)*0.02;
    }
    /** Validate the selected maneuver through pulse expiry AND its unpowered coast. */
    public static Forecast forecast(FlightPrediction.State start, Request r, Plan p, Corridor corridor, double minimumSpeed) {
        var s=start;
        double dx=-Math.sin(Math.toRadians(r.yaw)), dz=Math.cos(Math.toRadians(r.yaw));
        // Modded very long rockets are not assumed safe beyond a bounded planning budget.
        if (r.rocketTicks>80) return new Forecast(false,Double.NEGATIVE_INFINITY);
        // Hard proof of this finite pulse plus recovery remains separate from optional lookahead.
        // choose() inside this rollout still scores its extended collision forecast.
        int horizon=Math.max(24,r.rocketTicks+16);
        double goal=r.height;
        Plan active=p;
        double slopeCommand=p.pitch;
        Request trajectory=r;
        double bestMiss=Double.POSITIVE_INFINITY;
        boolean passed=false;
        for (int tick=0;tick<horizon;tick++) {
            double remaining=r.distance-((s.x()-start.x())*dx+(s.z()-start.z())*dz);
            boolean switchExit=trajectory.exit!=null && !trajectory.recovery && exitDue(r,remaining);
            if(switchExit) trajectory=exitRequest(s,trajectory,Math.max(0,r.rocketTicks-tick));
            boolean exiting=trajectory!=r || trajectory.recovery&&trajectory.exit!=null;
            passed|=remaining<-.5;
            int local=tick%8;
            if (switchExit || tick>0 && local==0) {
                var future=new Request(trajectory.yaw,trajectory.height,exiting?0:remaining,trajectory.slope,trajectory.flare,r.floor,Math.max(0,r.rocketTicks-tick),
                    trajectory.yawRate,trajectory.pitchRate,trajectory.recoveryRate,r.gravity,r.sideMargin,r.verticalMargin,trajectory.recovery,
                    exiting||r.aim==null||r.exit==null?null:r.aim.after(tick),trajectory.exit);
                active=choose(s,future,corridor);
                if (!ordinary(active)) return new Forecast(false,Double.NEGATIVE_INFINITY);
            }
            goal=FlightPrediction.pathHeight(exiting?0:remaining,trajectory.height,trajectory.slope,trajectory.flare)+active.heightOffset;
            if(exiting) goal=Math.max(goal,trajectory.exit.supportHeight(s)+active.heightOffset);
            double goalVelocity=FlightPrediction.pathVerticalSpeed(s,exiting?0:remaining,trajectory.slope,trajectory.flare);
            if(exiting && Double.isFinite(trajectory.exit.gradientX) && trajectory.exit.supportHeight(s)>=trajectory.height)
                goalVelocity=Math.max(0,trajectory.exit.gradientX*s.vx()+trajectory.exit.gradientZ*s.vz());
            double pitch=local<2?active.pitch:feedbackPitch(s,goal,goalVelocity,r.gravity,tick<r.rocketTicks);
            if(trajectory.flare<0 && local>=2) {
                if((local-2)%4==0) slopeCommand=FlightPrediction.choose(s,r.yaw,r.height+active.heightOffset,remaining,r.slope,r.flare,Double.NaN,
                    Math.max(0,r.rocketTicks-tick),r.yawRate,r.pitchRate,r.recoveryRate,r.gravity).pitch();
                pitch=slopeCommand;
            }
            if(r.aim!=null && tick>=r.aim.readyIn()) {
                var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),
                    FlightPrediction.turn(s.yaw(),local<6?active.yaw:trajectory.yaw,active.yawRate),
                    FlightPrediction.turn(s.pitch(),pitch,active.pitchRate));
                bestMiss=Math.min(bestMiss,r.aim.missSquared(aimed,tick));
            }
            var next=FlightPrediction.step(s,local<6?active.yaw:trajectory.yaw,pitch,active.yawRate,
                active.mode==Mode.PRECISION?active.pitchRate:trajectory.pitchRate,
                active.mode==Mode.PRECISION?active.pitchRate:trajectory.recoveryRate,r.gravity,tick<r.rocketTicks);
            if (!corridor.clear(s,next,0.04,0.04)) return new Forecast(false,Double.NEGATIVE_INFINITY);
            s=next;
        }
        // A short pulse ending before an uphill contact must leave enough energy to reach that
        // contact/exit, not just enough to hover over the earlier, lower section of the slope.
        if(r.aim!=null && !passed) goal=Math.max(goal,Math.max(r.height,r.exit==null?r.height:r.exit.y));
        return new Forecast(true,energy(s,goal,r.gravity)-0.5*minimumSpeed*minimumSpeed,passed,bestMiss);
    }
    static double feedbackPitch(FlightPrediction.State s,double goal,double goalVelocity,double gravity,boolean boost) {
        if (boost) {
            double vertical=Math.clamp(goalVelocity+(goal-s.y())*.22-(s.vy()-goalVelocity)*.6,-.8,.8);
            return -Math.toDegrees(Math.atan2(vertical,1.7));
        }
        // Vanilla's pull-up term converts horizontal kinetic energy to vertical motion.
        double conversion=(gravity*.25+(goal-s.y())*.04-(s.vy()-goalVelocity)*.3)/(Math.max(.2,Math.hypot(s.vx(),s.vz()))*.128);
        return Math.clamp(-Math.toDegrees(Math.asin(Math.clamp(conversion,-.7,.7))),-45,40);
    }
    /** Forecast the coast after this finite pulse. Never treat a firework as persistent thrust. */
    public static double coastReserve(FlightPrediction.State start, double recoveryY, double gravity, int boostTicks) {
        var s=start;
        int horizon=Math.min(90,Math.max(24,boostTicks+16));
        for (int tick=0;tick<horizon;tick++) s=FlightPrediction.step(s,start.yaw(),-8,5,3,6,gravity,tick<boostTicks);
        return energy(s,recoveryY,gravity)-0.5*0.65*0.65;
    }
}
