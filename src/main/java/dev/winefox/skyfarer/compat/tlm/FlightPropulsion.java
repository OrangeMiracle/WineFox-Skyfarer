package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** Main-thread flight/provider boundary. Guided execution owns steering; attached execution uses
 * the existing vanilla force projection. These control contracts are intentionally different. */
public final class FlightPropulsion {
    private final PropulsionCapability capability=new PropulsionCapability();
    private final RiptidePropulsion guided=new RiptidePropulsion();
    private final FireworkProvider attached;
    private boolean sustaining;
    public FlightPropulsion(){this(false);}
    public FlightPropulsion(boolean dispatchTransport){attached=new FireworkProvider(dispatchTransport);}
    public record Decision(boolean guided,boolean attached,boolean waiting,String reason) {}
    public Decision request(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,boolean canWait,
                            PropulsionRoute route,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        capability.refresh(maid,guided,attached.motor);capability.synchronize(planning);
        attached.resetReview();
        var requirement=route instanceof PropulsionRoute.Turn turn?demand.withTurn(turn.goal(),turn.rate()):demand;
        var decision=capability.decide(maid,start,requirement,false,canWait,guided,attached.motor,planning,heading,space,
            duration->attached.certify(start,requirement,route,planning,space,duration));
        return new Decision(decision.guided(),decision.firework(),decision.waiting(),decision.reason());
    }
    public int prefer(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        capability.refresh(maid,guided,attached.motor);capability.synchronize(planning);
        return capability.prefer(maid,demand.departure()?RiptidePropulsion.jumpStart(maid,planning.physics()):start,demand,attached.motor.active(),guided,planning,heading,space);
    }
    public void warm(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,GlidePlanner.Corridor space){capability.warm(maid,start,demand,planning,space);}
    public void warm(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,PropulsionRoute route,AsyncPlanning planning,GlidePlanner.Corridor space){
        var requirement=route instanceof PropulsionRoute.Turn turn?demand.withTurn(turn.goal(),turn.rate()):demand;
        warm(maid,start,requirement,planning,space);attached.warm(maid,start,requirement,route,planning,space);
    }
    public boolean available(EntityMaid maid){return CometBaubles.virtual(maid)||attached.motor.active()||guided.active()||RiptidePropulsion.available(maid)>0||!attached.durations(maid).isEmpty();}
    public boolean attached(){return attached.motor.active();}
    /** Numeric projection for the vanilla attached-force integrator only, never a Riptide duration. */
    public int projectedTicks(){return attached.motor.remainingBoostTicks();}
    public int projectionDelay(EntityMaid maid){return attached.motor.ignitionDelay(maid);}
    public double projectedSpeedFloor(){return attached()?1.7:0;}
    public boolean guided(){return guided.active()||capability.virtual.active();}
    public boolean riptide(){return guided.active();}
    public boolean virtual(){return capability.virtual.active()||sustaining;}
    public String guidedSource(){return capability.selected()==PropulsionDemand.Source.VIRTUAL_PROPULSION?"VIRTUAL_PROPULSION":"RIPTIDE";}
    public boolean interrupted(EntityMaid maid){return maid.isAutoSpinAttack()&&!guided.active();}
    public boolean continuation(){return capability.virtual.active()?capability.virtual.continuation():guided.continuation();}
    public CompactTurnPlanner.Goal turnGoal(){return capability.virtual.active()?capability.virtual.turnGoal():guided.turnGoal();}
    public boolean completed(){return capability.selected()==PropulsionDemand.Source.VIRTUAL_PROPULSION?capability.virtual.completed():guided.completed();}
    public boolean prepare(EntityMaid maid,boolean eligible){
        sustaining=false;boolean changed=capability.refresh(maid,guided,attached.motor);guided.prepare(maid,eligible);attached.begin(maid);return changed;
    }
    public void context(String phase){guided.context(phase);capability.virtual.context(phase);}
    public void continuationAim(FlightPrediction.Aim aim,int deadline){guided.continuationAim(aim,deadline);capability.virtual.continuationAim(aim,deadline);}
    public boolean tick(EntityMaid maid,FlightPrediction.State start,AsyncPlanning planning,HeadingController heading){capability.refresh(maid,guided,attached.motor);capability.synchronize(planning);return capability.virtual.active()?capability.virtual.tick(maid,start,planning,heading):guided.tick(maid,start,planning,heading);}
    /** All upper controllers may request speed hold. Only a registered continuous provider runs it. */
    public boolean sustain(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,HeadingController heading){
        capability.refresh(maid,guided,attached.motor);capability.synchronize(planning);
        if(guided()||attached())return false;
        sustaining=capability.sustain(maid,start,demand,planning,heading);return sustaining;
    }
    public void cancel(EntityMaid maid,String reason){sustaining=false;guided.cancel(maid,reason);capability.virtual.cancel(reason);}
    public void transition(EntityMaid maid,String old,String next,String reason){guided.transition(maid,old,next,reason);}
    public void observedStop(EntityMaid maid,String caller){guided.observedStop(maid,caller);}
    public void detach(){attached.motor.detach();}
    public void close(EntityMaid maid,String reason,boolean handoff){cancel(maid,reason);detach();resetLaunch();if(!handoff)RiptideReadiness.clear(maid);}
    public SweepingAttackPlanner.Selection attackSelection(){return capability.virtual.active()?capability.virtual.attackSelection():guided.attackSelection();}
    public List<GlidePlanner.TracePoint> guidedTrace(){return virtual()?capability.virtual.trace():guided.trace();}
    public String reason(){return capability.reason();}
    public String review(){return attached.review();}
    public GlidePlanner.Request selectedRequest(){return attached.selectedRequest();}
    public String detail(EntityMaid maid){
        var executing=virtual()?PropulsionDemand.Source.VIRTUAL_PROPULSION:guided.active()?PropulsionDemand.Source.RIPTIDE:attached()?PropulsionDemand.Source.FIREWORK:PropulsionDemand.Source.COAST;
        return "source="+executing+" eligible="+PropulsionCapability.source(maid)+" guided="+guided()+" virtual="+capability.virtual.reason()+" duration="+guided.selectedDuration()+" attached="+attached()+" cooldown="+attached.motor.cooldown(maid);
    }
    public EmergencyAvoidancePlanner.Plan emergency(EntityMaid maid,FlightPrediction.State start,AsyncPlanning planning,double gravity){
        return planning.emergency(start,projectedTicks(),attached.motor.cooldown(maid)==0?attached.durations(maid):List.of(),gravity);
    }
    public boolean executeEmergency(EntityMaid maid,FlightPrediction.State start,EmergencyAvoidancePlanner.Plan plan,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        if(plan.duration()<0)return true;
        double yaw=Math.toRadians(plan.yaw());
        var demand=new PropulsionDemand(PropulsionDemand.Phase.EMERGENCY,start.x()-Math.sin(yaw)*64,start.y()+3,start.z()+Math.cos(yaw)*64,.65);
        var result=capability.decide(maid,start,demand,false,false,guided,attached.motor,planning,heading,space,d->d==plan.duration()?0:Double.POSITIVE_INFINITY);
        return result.guided()||result.firework();
    }
    public boolean needsFunding(EmergencyAvoidancePlanner.Plan plan){return plan==null||plan.duration()>=0;}
    public boolean needsFunding(PlanningTask.RejoinResult plan){return plan.duration()>=0;}
    public LaunchAttackPlanner.Plan launchAttack(EntityMaid maid,FlightPrediction.State start,List<LaunchAttackPlanner.Target> targets,AsyncPlanning planning,double gravity,double rate,double minimum,double exit){
        return planning.launchAttack(start,targets,attached.durations(maid),gravity,rate,minimum,exit);
    }
    public PlanningTask.RejoinResult rejoin(EntityMaid maid,FlightPrediction.State start,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,AsyncPlanning planning,double gravity,double rate,double minimum,GlidePlanner.Corridor space,boolean departure){
        return departure?planning.launchRejoin(start,path,aim,projectedTicks(),gravity,rate,minimum,space,attached.durations(maid)):
            planning.rejoin(start,path,aim,projectedTicks(),gravity,rate,minimum,space,attached.attackDurations(maid));
    }
    public boolean executeRejoin(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,PlanningTask.RejoinResult proof,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        if(proof.duration()<0)return true;
        var result=requestRejoin(maid,start,demand,proof,planning,heading,space);return result.guided()||result.attached();
    }
    public Decision requestRejoin(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,PlanningTask.RejoinResult proof,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        var result=capability.decide(maid,start,demand,false,true,guided,attached.motor,planning,heading,space,d->d==proof.duration()?0:Double.POSITIVE_INFINITY);
        return new Decision(result.guided(),result.firework(),result.waiting(),result.reason());
    }
    public record FundedSearch(SweepingAttackPlanner.Selection selection,boolean pending) {}
    public FundedSearch fundedSearch(EntityMaid maid,FlightPrediction.State start,SweepingAttackPlanner.Point target,FlightPrediction.Aim aim,AsyncPlanning planning,double gravity,double rate,double minimum,double exit,GlidePlanner.Corridor space,double turn){
        SweepingAttackPlanner.Selection best=null;boolean pending=false;
        for(int duration:attached.attackDurations(maid)){
            var choice=planning.fundedSweep(start,target,aim,duration,gravity,rate,minimum,exit,space,turn);pending|=planning.lastPending();
            if(choice!=null&&(best==null||choice.prediction().cost()<best.prediction().cost()))best=choice;
        }
        return new FundedSearch(best,pending);
    }
    private int preparedDuration=-1;
    private FlightPrediction.State preparedStart;
    public boolean prepared(FlightPrediction.State s){return preparedStart!=null&&preparedDuration>=0&&Math.hypot(s.x()-preparedStart.x(),s.z()-preparedStart.z())<.75&&Math.abs(s.y()-preparedStart.y())<.65&&Math.abs(s.vy()-preparedStart.vy())<.3;}
    public boolean unpoweredLaunch(){return preparedDuration==-2;}
    public void resetLaunch(){preparedDuration=-1;preparedStart=null;}
    public SurfaceLaunchPlanner.Plan launch(EntityMaid maid,FlightPrediction.State start,double yaw,double height,AsyncPlanning planning,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water){
        SurfaceLaunchPlanner.Plan best=null;
        for(int duration:attached.durations(maid)){
            var plan=planning.launch(start,yaw,height,gravity,duration,space,water);
            if(plan!=null&&(best==null||plan.cost()<best.cost())){best=plan;preparedDuration=duration;preparedStart=start;}
        }
        if(best==null&&!planning.lastPending()){
            best=planning.coastLaunch(start,yaw,height,gravity,water);
            if(best!=null){preparedDuration=-2;preparedStart=start;}
        }
        return best;
    }
    public boolean executeLaunch(EntityMaid maid,FlightPrediction.State start,SurfaceLaunchPlanner.Plan plan,boolean prepared,AsyncPlanning planning,double gravity,GlidePlanner.Corridor space,SurfaceLaunchPlanner.Water water,HeadingController heading){
        double yaw=Math.toRadians(plan.yaw());
        var demand=new PropulsionDemand(PropulsionDemand.Phase.TAKEOFF,start.x()-Math.sin(yaw)*64,plan.height(),start.z()+Math.cos(yaw)*64,.9);
        var result=capability.decide(maid,start,demand,false,true,guided,attached.motor,planning,heading,space,
            prepared?duration->duration==preparedDuration?plan.cost():Double.POSITIVE_INFINITY:duration->planning.launchPulse(start,plan,gravity,duration,space,water));
        return result.guided()||result.firework();
    }
}
