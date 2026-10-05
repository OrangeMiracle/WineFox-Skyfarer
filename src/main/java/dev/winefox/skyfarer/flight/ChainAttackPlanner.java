package dev.winefox.skyfarer.flight;

/** Short, unpowered pursuit. Every proposed contact includes a collision-free pull-out. */
public final class ChainAttackPlanner {
    private ChainAttackPlanner() {}
    public record Plan(CompactTurnPlanner.Command command,FlightPrediction.State contact,int ticks,double cost) {}
    public static Plan choose(FlightPrediction.State start,FlightPrediction.Aim aim,double height,int rocketTicks,
        double gravity,double minimumSpeed,int budget,double distanceBudget,GlidePlanner.Corridor space) {
        Plan best=null;
        for(double rate:new double[]{5,8,10,12}) {
            var s=start; double travelled=0; CompactTurnPlanner.Command first=null;
            for(int tick=0;tick<Math.min(32,budget);tick++) {
                double x=aim.x()+aim.vx()*tick,z=aim.z()+aim.vz()*tick;
                double yaw=Math.toDegrees(Math.atan2(s.x()-x,z-s.z()));
                double pitch=GlidePlanner.feedbackPitch(s,height+aim.vy()*tick,0,gravity,RocketProfile.active(tick,rocketTicks,start));
                pitch=Math.clamp(pitch,-35,30);
                var c=new CompactTurnPlanner.Command(yaw,pitch,rate,6);
                if(first==null) first=c;
                var look=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),
                    FlightPrediction.turn(s.yaw(),yaw,rate),FlightPrediction.turn(s.pitch(),pitch,6));
                double speed=Math.hypot(s.vx(),s.vz());
                if(tick>=aim.readyIn() && speed>=minimumSpeed && aim.missSquared(look,tick)<=.015) {
                    if(recovery(s,RocketProfile.remaining(tick,rocketTicks,start),gravity,space)) {
                        double cost=tick+rate*.3+travelled*.2;
                        if(best==null || cost<best.cost) best=new Plan(first,s,tick,cost);
                    }
                    break;
                }
                var next=FlightPrediction.step(s,yaw,pitch,rate,6,6,gravity,RocketProfile.active(tick,rocketTicks,start));
                travelled+=Math.sqrt(Math.pow(next.x()-s.x(),2)+Math.pow(next.y()-s.y(),2)+Math.pow(next.z()-s.z(),2));
                if(travelled>distanceBudget || !space.clear(s,next,.04,.04)) break;
                s=next;
            }
        }
        return best;
    }
    private static boolean recovery(FlightPrediction.State s,int rocket,double gravity,GlidePlanner.Corridor space) {
        var initial=s;
        double height=s.y()+5;
        for(int tick=0;tick<Math.max(18,rocket+initial.rocketDelay()+12);tick++) {
            boolean powered=RocketProfile.active(tick,rocket,initial);
            var next=FlightPrediction.step(s,s.yaw(),GlidePlanner.feedbackPitch(s,height,0,gravity,powered),5,6,10,gravity,powered);
            if(!space.clear(s,next,.04,.04)) return false;
            s=next;
        }
        return true;
    }
    /** Estimate contact on the current primary route without steering toward a secondary. */
    public static FlightPrediction.State primaryContact(FlightPrediction.State start,GlidePlanner.Request request,GlidePlanner.Corridor space) {
        if(request.aim()==null) return null;
        var s=start; double dx=-Math.sin(Math.toRadians(request.yaw())),dz=Math.cos(Math.toRadians(request.yaw()));
        for(int tick=0;tick<20;tick++) {
            double left=request.distance()-(s.x()-start.x())*dx-(s.z()-start.z())*dz;
            var r=new GlidePlanner.Request(request.yaw(),request.height(),left,request.slope(),request.flare(),request.floor(),
                RocketProfile.remaining(tick,request.rocketTicks(),start),request.yawRate(),request.pitchRate(),request.recoveryRate(),request.gravity(),
                request.sideMargin(),request.verticalMargin(),false,request.aim().after(tick),request.exit());
            var p=GlidePlanner.choose(s,r,space);
            if(!GlidePlanner.ordinary(p)) return null;
            var look=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),p.yaw(),p.yawRate()),
                FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate()));
            if(tick>=request.aim().readyIn() && request.aim().missSquared(look,tick)<=.015) return look.withPhysics(s.physics()).withRocketDelay(s.rocketDelay());
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),request.gravity(),RocketProfile.active(tick,request.rocketTicks(),start));
            if(!space.clear(s,next,.04,.04)) return null;
            s=next;
        }
        return null;
    }
}
