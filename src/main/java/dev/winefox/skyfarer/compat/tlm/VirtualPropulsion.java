package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** One finite, re-certified force schedule. The common flight controller retains route/state ownership.
 * No entities, native spin, item use, durability, direct travel, or worker access to equipment. */
public final class VirtualPropulsion implements PropulsionResolver.Provider {
    public PropulsionDemand.Source source(){return PropulsionDemand.Source.VIRTUAL_PROPULSION;}
    public void warm(PropulsionResolver.Context c){c.planning().virtualPropulsion(c.start(),c.demand(),c.maid().isInWaterOrBubble(),c.space());}
    public boolean sustain(PropulsionResolver.Context c){return sustain(c.maid(),c.start(),c.demand(),c.planning(),c.heading());}
    public PropulsionResolver.Offer offer(PropulsionResolver.Context c){
        var candidate=c.planning().virtualPropulsion(c.start(),c.demand(),c.maid().isInWaterOrBubble(),c.space());
        boolean pending=c.planning().lastPending();
        if(candidate!=null&&begin(c.maid(),candidate,c.start(),c.planning(),c.heading()))return new PropulsionResolver.Offer(true,false,"VIRTUAL_PROPULSION_ACCEPTED");
        String why=candidate==null?pending?"VIRTUAL_CERTIFICATE_PENDING":"NO_SAFE_VIRTUAL_PROPULSION":reason();
        return new PropulsionResolver.Offer(false,pending||why.startsWith("SNAPSHOT_MISSING"),why);
    }
    private RiptidePlanner.Plan plan;
    private long started,lastTick=Long.MIN_VALUE;
    private boolean jumping,completed;
    private String reason="NONE";
    private boolean transit;
    public void context(String phase){transit="TRANSIT".equals(phase);}
    private List<GlidePlanner.TracePoint> trace=List.of();
    /** Cruise does not own a long-lived route or delay the state machine. One complete current
     * proof permits one impulse; an unavailable snapshot permits none. */
    public boolean sustain(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,HeadingController heading){
        if(active()||!CometBaubles.virtual(maid)||maid.onGround()||!maid.isFallFlying()||maid.isAutoSpinAttack()
            ||heading.owner()==HeadingController.Owner.SAFETY)return false;
        if(lastTick==maid.level().getGameTime())return true;
        var candidate=VirtualFlightControl.candidate(start,demand);if(candidate==null)return false;
        var proof=planning.certifyRiptide(start,candidate,0,maid.isInWaterOrBubble());
        if(proof==null){reason=planning.riptideRejection();return false;}
        if(!withinAnchor(maid,proof)){reason="ANCHOR_REJECTED";return false;}
        return apply(maid,start,proof,0,planning,heading);
    }
    public boolean active(){return plan!=null;}
    public boolean completed(){return completed;}
    public String reason(){return reason;}
    public boolean continuation(){return active()&&plan.demand()!=null&&plan.demand().continuation();}
    public CompactTurnPlanner.Goal turnGoal(){return active()&&plan.demand()!=null?plan.demand().turn():null;}
    public List<GlidePlanner.TracePoint> trace(){return trace;}
    public void continuationAim(FlightPrediction.Aim aim,int deadline){if(continuation()&&(plan.demand().exiting()||aim!=null))plan=plan.withDemand(plan.demand().withAim(aim).withDeadline(deadline));}
    public boolean begin(EntityMaid maid,RiptidePlanner.Plan candidate,FlightPrediction.State start,AsyncPlanning planning,HeadingController heading){
        completed=false;reason="VIRTUAL_UNAVAILABLE";
        if(lastTick==maid.level().getGameTime()){reason="ALREADY_APPLIED_THIS_TICK";return false;}
        if(candidate==null||!candidate.profile().virtual()||!CometBaubles.virtual(maid)||maid.isAutoSpinAttack()||active())return false;
        boolean launch=candidate.departure()&&maid.onGround();
        if(!launch&&!maid.isFallFlying())return false;
        if(launch){
            if(maid.isInWaterOrBubble()||!new dev.winefox.skyfarer.safety.CorridorProbe(maid).segment(maid.getEyePosition().add(0,.01,0),maid.getEyePosition().add(0,.43,0),.02,0))return false;
            start=RiptidePlanner.aimLaunch(RiptidePropulsion.jumpStart(maid,planning.physics()),candidate.yaw(),candidate.pitch());
        }
        var proof=planning.certifyRiptide(start,candidate,0,maid.isInWaterOrBubble());
        if(proof==null){reason=planning.riptideRejection();return false;}
        if(!withinAnchor(maid,proof)){reason="ANCHOR_REJECTED";return false;}
        plan=proof;started=maid.level().getGameTime();jumping=launch;
        if(launch){
            heading.steerAngles(maid,plan.yaw(),plan.pitch(),180,90,HeadingController.Owner.PROPULSION);
            maid.getJumpControl().jump();reason="VIRTUAL_JUMP_RESERVED";return true;
        }
        return tick(maid,start,planning,heading);
    }
    public boolean tick(EntityMaid maid,FlightPrediction.State start,AsyncPlanning planning,HeadingController heading){
        if(!active())return false;
        if(heading.owner()==HeadingController.Owner.SAFETY){cancel("SAFETY_PREEMPTED");return false;}
        if(!CometBaubles.virtual(maid)||maid.isAutoSpinAttack()){cancel("VIRTUAL_EQUIPMENT_OR_CONTROL_LOST");return false;}
        long now=maid.level().getGameTime();if(lastTick==now)return true;
        if(jumping){
            if(maid.onGround()){
                if(now-started>5){cancel("VIRTUAL_JUMP_TIMEOUT");return false;}
                return true;
            }
            if(!ElytraFlightController.startGliding(maid)){cancel("VIRTUAL_GLIDE_REJECTED");return false;}
            // startGliding changes pose, eye height and swept body dimensions. The caller's
            // standing-eye state was captured before that transition and cannot certify
            // this first flying impulse. Use the current entity after the pose change.
            var velocity=maid.getDeltaMovement();
            start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),velocity.x,velocity.y,velocity.z,
                maid.getYRot(),maid.getXRot(),planning.physics());
            jumping=false;started=now;
        }
        if(maid.onGround()||!maid.isFallFlying()){cancel("VIRTUAL_GLIDE_LOST");return false;}
        int elapsed=(int)(now-started);
        if(elapsed>=plan.end()){
            boolean ready=!plan.departure()||RiptidePlanner.departureReady(start,plan);
            cancel(ready?"COMPLETED":"VIRTUAL_HANDOFF_NOT_READY");return false;
        }
        if(plan.demand()!=null)plan=plan.withDemand(plan.demand().advanceTurn(start));
        var proof=planning.certifyRiptide(start,plan,elapsed,maid.isInWaterOrBubble());
        if(proof==null){cancel(planning.riptideRejection());return false;}
        if(!withinAnchor(maid,proof)){cancel("ANCHOR_REJECTED");return false;}
        return apply(maid,start,proof,elapsed,planning,heading);
    }
    private boolean apply(EntityMaid maid,FlightPrediction.State start,RiptidePlanner.Plan proof,int elapsed,AsyncPlanning planning,HeadingController heading){
        var next=RiptidePlanner.step(start,proof,elapsed,planning.physics().gravity(),maid.isInWaterOrBubble());
        if(!new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(start,next,.2,.15)){
            cancel("LIVE_BODY_BLOCKED");return false;
        }
        trace=proof.trace().stream().map(s->new GlidePlanner.TracePoint(s,.2,.15)).toList();
        var command=RiptidePlanner.command(start,proof,elapsed,planning.physics().gravity());
        heading.steerAngles(maid,command.yaw(),command.pitch(),command.yawRate(),command.pitchRate(),HeadingController.Owner.PROPULSION);
        float output=0;
        if(elapsed<proof.end()){
            var velocity=maid.getDeltaMovement();
            output=proof.profile().force(velocity.x,velocity.y,velocity.z);
            var force=RiptidePlanner.impulse(maid.getYRot(),maid.getXRot(),output);
            maid.push(force.x(),force.y(),force.z());
        }
        lastTick=maid.level().getGameTime();reason=proof.profile().regulated()?"VIRTUAL_SPEED_HOLD":"VIRTUAL_PROPULSION";
        effects(maid,output);return true;
    }
    private void effects(EntityMaid maid,float output){dev.winefox.skyfarer.runtime.CometThrustEffects.emit(maid,output);}
    private boolean withinAnchor(EntityMaid maid,RiptidePlanner.Plan proof){
        // TAKEOFF is still a departure demand, but a remote departure belongs to the
        // local transit window, not a combat disk centred hundreds of blocks away.
        boolean localTransit=transit||proof.demand()!=null&&proof.demand().phase()==PropulsionDemand.Phase.TRANSIT;
        var anchor=localTransit?maid.position():dev.winefox.skyfarer.ai.CombatAnchor.current(maid);
        double radius=localTransit?128:Math.max(dev.winefox.skyfarer.ai.TargetEvaluator.hardRange(maid)+12,maid.position().subtract(anchor).horizontalDistance()+6);
        return proof.trace().stream().allMatch(s->Math.hypot(s.x()-anchor.x,s.z()-anchor.z)<=radius);
    }
    public SweepingAttackPlanner.Selection attackSelection(){
        if(!continuation()||plan.demand().exiting()||plan.demand().aim()==null)return null;
        var d=plan.demand();var first=plan.trace().getFirst();int contact=0;double miss=Double.POSITIVE_INFINITY;
        for(int i=0;i<plan.trace().size();i++){double value=d.aim().missSquared(plan.trace().get(i),i);if(value<miss){miss=value;contact=i;}}
        return new SweepingAttackPlanner.Selection(d.path(),new SweepingAttackPlanner.Rollout(true,true,contact,plan.cost(),miss,
            RiptidePlanner.command(first,plan,0,first.physics()==null?.08:first.physics().gravity()),plan.trace().stream().map(s->new GlidePlanner.TracePoint(s,.2,.15)).toList()));
    }
    public void cancel(String why){plan=null;trace=List.of();jumping=false;completed="COMPLETED".equals(why);reason=why;}
}
