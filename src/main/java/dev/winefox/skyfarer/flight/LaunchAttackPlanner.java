package dev.winefox.skyfarer.flight;

import java.util.List;

/** Ground heading is free; everything after the jump uses the normal attack/exit simulator. */
public final class LaunchAttackPlanner {
    private LaunchAttackPlanner() {}
    public record Target(java.util.UUID id,FlightPrediction.Aim aim) {}
    public record Plan(java.util.UUID target,double yaw,double pitch,int duration,SweepingAttackPlanner.Selection selection) {}
    public static Plan choose(FlightPrediction.State jump,List<Target> targets,List<Integer> durations,double gravity,double rate,double minimum,
                              double exit,GlidePlanner.Corridor space,SweepingAttackPlanner.Contact contact){
        Plan best=null;
        for(var target:targets.stream().limit(4).toList()){
            var aim=target.aim();var point=new SweepingAttackPlanner.Point(aim.x(),aim.bottom()+aim.height()*.5,aim.z());
            var origin=SweepingAttackPlanner.point(jump);var delta=point.subtract(origin);
            if(delta.y()<1||delta.length()>40)continue;
            double direct=Math.toDegrees(Math.atan2(-delta.x(),delta.z()));
            for(double elevation:new double[]{30,50,70})for(double turn:new double[]{0,-30,30}){
                double yaw=direct+turn;var look=VanillaElytra.look(yaw,-elevation);
                var direction=new SweepingAttackPlanner.Point(look.x(),look.y(),look.z());
                var launch=new FlightPrediction.State(jump.x(),jump.y(),jump.z(),jump.vx(),jump.vy(),jump.vz(),yaw,-elevation,jump.physics());
                for(double side:new double[]{0,-60,60}){
                    double a=Math.toRadians(direct+side);
                    var outward=new SweepingAttackPlanner.Point(-Math.sin(a),.15,Math.cos(a)).unit();
                    var end=point.add(outward.scale(exit)).add(new SweepingAttackPlanner.Point(0,3,0));
                    var path=new SweepingAttackPlanner.Path(
                        new SweepingAttackPlanner.Cubic(origin,origin.add(direction.scale(Math.clamp(delta.length()*.4,2,8))),point.subtract(direction.scale(2.5)),point),
                        new SweepingAttackPlanner.Cubic(point,point.add(direction.scale(2.5)),end.subtract(outward.scale(exit*.4)),end),"VERTICAL_LAUNCH",Math.abs(side)*.01);
                    if(!path.clear(space,.15,.12))continue;
                    var coast=SweepingAttackPlanner.simulate(launch,path,aim,0,gravity,rate,minimum,false,space,contact,120);
                    if(coast.safe()&&coast.hit()){
                        var candidate=new Plan(target.id(),yaw,-elevation,-1,new SweepingAttackPlanner.Selection(path,coast));
                        if(best==null||cost(candidate)<cost(best))best=candidate;
                        continue;
                    }
                    for(int duration:durations.stream().distinct().limit(3).toList()){
                        if(RocketProfile.maximumTicks(duration)>64)continue;
                        SweepingAttackPlanner.Rollout proof=null;boolean safe=true;
                        for(int life=RocketProfile.minimumTicks(duration);life<=RocketProfile.maximumTicks(duration);life++){
                            proof=SweepingAttackPlanner.simulate(launch.withRocketDelay(1),path,aim,life,gravity,rate,minimum,false,space,contact,120);
                            if(!proof.safe()||!proof.hit()){safe=false;break;}
                        }
                        if(safe){var candidate=new Plan(target.id(),yaw,-elevation,duration,new SweepingAttackPlanner.Selection(path,proof));
                            if(best==null||cost(candidate)<cost(best))best=candidate;}
                    }
                }
            }
        }
        return best;
    }
    private static double cost(Plan p){return p.selection.prediction().cost()+(p.duration<0?0:8)+p.selection.prediction().contactTick()*.5;}
}
