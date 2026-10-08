package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** One live qualification and candidate arbitration boundary; physics remains provider-specific.
 * A caller supplies its flight requirement and certifies coast first. 0 permits firework fallback,
 * 1 means bounded preparation/prediction wait, 2 means Riptide has actually reserved execution. */
public final class PropulsionCapability {
    public record Decision(PropulsionDemand.Source source,boolean waiting,String reason){
        public boolean riptide(){return source==PropulsionDemand.Source.RIPTIDE;}
        public boolean firework(){return source==PropulsionDemand.Source.FIREWORK;}
    }
    /** Provider-specific proofs remain necessary: a random-lived rocket is not a timed trident.
     * The callback is evaluated on the owner thread only, never stored or sent to a worker. */
    public Decision decide(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,boolean coastSufficient,boolean canWait,
            RiptidePropulsion motor,FireworkManager fireworks,AsyncPlanning planning,HeadingController heading,
            GlidePlanner.Corridor space,java.util.function.IntToDoubleFunction rocketCertificate){
        if(coastSufficient)return new Decision(PropulsionDemand.Source.COAST,false,"COAST_SUFFICIENT");
        int selected=prefer(maid,start,demand,fireworks.active(),motor,planning,heading,space);
        if(selected==2)return new Decision(PropulsionDemand.Source.RIPTIDE,false,reason);
        if(selected==1&&canWait)return new Decision(null,true,reason);
        if(fireworks.boost(maid,rocketCertificate))return new Decision(PropulsionDemand.Source.FIREWORK,false,fireworks.decision());
        return new Decision(null,false,reason+";"+fireworks.decision());
    }
    public record Snapshot(List<Float> riptide,List<Integer> fireworks,boolean wet){
        public Snapshot{riptide=List.copyOf(riptide);fireworks=List.copyOf(fireworks);}
        public boolean available(){return !riptide.isEmpty()||!fireworks.isEmpty();}
        public String source(){return !riptide.isEmpty()?"RIPTIDE":!fireworks.isEmpty()?"FIREWORK":"NONE";}
    }
    public static Snapshot inspect(EntityMaid maid){
        return new Snapshot(RiptidePropulsion.strengths(maid),FireworkManager.durations(maid),maid.isInWaterOrRain());
    }
    public static String source(EntityMaid maid){return inspect(maid).source();}
    /** Saved registry records cannot know live weather. Admission always takes a fresh snapshot. */
    public static String rejection(EntityMaid maid){
        var snapshot=inspect(maid);
        return snapshot.available()?null:snapshot.wet()?"no_firework_or_usable_riptide":"no_firework_riptide_environment_invalid";
    }
    private int waiting;
    private PropulsionDemand.Phase phase;
    private String reason="NONE";
    public String reason(){return reason;}
    public void warm(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,GlidePlanner.Corridor space){
        var resource=RiptideReadiness.of(maid);
        if(resource.strength()>0)planning.propulsion(start,demand,resource.strength(),maid.isInWaterOrBubble(),space);
    }
    public int prefer(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,boolean activeRocket,
            RiptidePropulsion motor,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        if(phase!=demand.phase()){phase=demand.phase();waiting=0;}
        if(activeRocket||motor.active()||maid.isAutoSpinAttack())return reject("ACTIVE_PROPULSION");
        var resource=RiptideReadiness.of(maid);
        if(!maid.isInWaterOrRain()||resource.strength()<=0)return reject("ENVIRONMENT_OR_TRIDENT_UNAVAILABLE");
        // Prewarm with the SAME requirement even while independent preparation is incomplete.
        var candidate=planning.propulsion(start,demand,resource.strength(),maid.isInWaterOrBubble(),space);
        boolean pending=planning.lastPending();
        if(motor.ready(maid)&&candidate!=null&&(demand.departure()?motor.beginLaunch(maid,candidate,planning,heading):motor.begin(maid,candidate,start,planning,heading))){
            reason="RIPTIDE_ACCEPTED";waiting=0;return 2;
        }
        reason=!motor.ready(maid)?"PRECHARGE_"+resource.ticks():candidate==null?pending?"CERTIFICATE_PENDING":"NO_SAFE_RIPTIDE":motor.beginRejection();
        if(candidate!=null&&motor.ready(maid)&&!motor.beginRejection().startsWith("SNAPSHOT_MISSING"))return reject(motor.beginRejection());
        // Emergency never waits for a future resource; it may immediately use a certified rocket.
        if(!demand.emergency()&&(pending||candidate!=null||!motor.ready(maid))&&++waiting<(demand.departure()?40:12))return 1;
        waiting=0;return 0;
    }
    private int reject(String why){reason=why;waiting=0;return 0;}
}
