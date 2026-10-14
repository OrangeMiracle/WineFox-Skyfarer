package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** One live qualification and candidate arbitration boundary; physics remains provider-specific.
 * Resolver owns source order and exclusive policies. Providers own proof and commit.
 * Legacy numeric prefer results express unavailable / waiting / guided execution only. */
public final class PropulsionCapability {
    final VirtualPropulsion virtual=new VirtualPropulsion();
    private PropulsionDemand.Source selected;
    public PropulsionDemand.Source selected(){return selected;}
    public record Decision(PropulsionDemand.Source source,boolean waiting,String reason){
        public boolean riptide(){return source==PropulsionDemand.Source.RIPTIDE;}
        public boolean firework(){return source==PropulsionDemand.Source.FIREWORK;}
        public boolean guided(){return riptide()||source==PropulsionDemand.Source.VIRTUAL_PROPULSION;}
    }
    private final PropulsionResolver resolver=new PropulsionResolver(List.of(virtual,new RiptideProvider(),new PropulsionResolver.Provider(){
        public PropulsionDemand.Source source(){return PropulsionDemand.Source.FIREWORK;}
        public PropulsionResolver.Offer offer(PropulsionResolver.Context c){
            boolean accepted=c.fireworks().boost(c.maid(),c.rocketCertificate());
            return new PropulsionResolver.Offer(accepted,false,c.fireworks().decision());
        }
    }));
    private PropulsionDemand.Source equipmentPolicy;
    private boolean policyKnown,dirty;
    private String reason="NONE";
    public String reason(){return reason;}
    public boolean refresh(EntityMaid maid,RiptidePropulsion motor,FireworkManager fireworks){
        var current=PropulsionResolver.exclusive(maid);
        boolean changed=policyKnown&&equipmentPolicy!=current;policyKnown=true;equipmentPolicy=current;
        if(changed){virtual.cancel("SOURCE_CHANGED");motor.cancel(maid,"SOURCE_CHANGED");fireworks.detach();selected=null;resolver.reset();dirty=true;}
        return changed;
    }
    public void synchronize(AsyncPlanning planning){if(dirty){planning.invalidate();dirty=false;}}
    public Decision decide(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,boolean coastSufficient,boolean canWait,
            RiptidePropulsion motor,FireworkManager fireworks,AsyncPlanning planning,HeadingController heading,
            GlidePlanner.Corridor space,java.util.function.IntToDoubleFunction rocketCertificate){
        refresh(maid,motor,fireworks);synchronize(planning);
        if(heading.owner()==HeadingController.Owner.SAFETY)return new Decision(null,false,"SAFETY_OWNS_CONTROL");
        if(virtual.active())return new Decision(PropulsionDemand.Source.VIRTUAL_PROPULSION,false,"GUIDED_OWNER_CONTINUES");
        if(motor.active())return new Decision(PropulsionDemand.Source.RIPTIDE,false,"GUIDED_OWNER_CONTINUES");
        if(fireworks.active())return new Decision(PropulsionDemand.Source.FIREWORK,false,"ATTACHED_OWNER_CONTINUES");
        if(maid.isAutoSpinAttack())return new Decision(null,false,"EXTERNAL_SPIN_ACTIVE");
        if(coastSufficient)return new Decision(PropulsionDemand.Source.COAST,false,"COAST_SUFFICIENT");
        var result=resolver.resolve(new PropulsionResolver.Context(maid,start,demand,motor,fireworks,planning,heading,space,rocketCertificate),canWait,true);
        if(result.source()!=null)selected=result.source();reason=result.reason();
        return new Decision(result.source(),result.waiting(),reason);
    }
    public record Snapshot(List<Float> riptide,List<Integer> fireworks,boolean wet,boolean virtual){
        public Snapshot(List<Float> riptide,List<Integer> fireworks,boolean wet){this(riptide,fireworks,wet,false);}
        public Snapshot{riptide=List.copyOf(riptide);fireworks=List.copyOf(fireworks);}
        public boolean available(){return virtual||!riptide.isEmpty()||!fireworks.isEmpty();}
        public String source(){return virtual?"VIRTUAL_PROPULSION":!riptide.isEmpty()?"RIPTIDE":!fireworks.isEmpty()?"FIREWORK":"NONE";}
    }
    public static Snapshot inspect(EntityMaid maid){
        return CometBaubles.virtual(maid)?new Snapshot(List.of(),List.of(),maid.isInWaterOrRain(),true):new Snapshot(RiptidePropulsion.strengths(maid),FireworkManager.durations(maid),maid.isInWaterOrRain(),false);
    }
    public static String source(EntityMaid maid){return inspect(maid).source();}
    /** Saved registry records cannot know live weather. Admission always takes a fresh snapshot. */
    public static String rejection(EntityMaid maid){
        var snapshot=inspect(maid);
        return snapshot.available()?null:snapshot.wet()?"no_firework_or_usable_riptide":"no_firework_riptide_environment_invalid";
    }
    public void warm(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,GlidePlanner.Corridor space){
        synchronize(planning);
        resolver.warm(new PropulsionResolver.Context(maid,start,demand,null,null,planning,null,space,null));
    }
    public boolean sustain(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,AsyncPlanning planning,HeadingController heading){
        var result=resolver.sustain(new PropulsionResolver.Context(maid,start,demand,null,null,planning,heading,null,null));
        if(result.source()!=null){selected=result.source();reason=result.reason();return true;}return false;
    }
    public int prefer(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,boolean activeRocket,
            RiptidePropulsion motor,AsyncPlanning planning,HeadingController heading,GlidePlanner.Corridor space){
        synchronize(planning);
        if(heading.owner()==HeadingController.Owner.SAFETY){reason="SAFETY_OWNS_CONTROL";return 0;}
        if(activeRocket||motor.active()||virtual.active()||maid.isAutoSpinAttack()){reason="ACTIVE_PROPULSION";return 0;}
        var result=resolver.resolve(new PropulsionResolver.Context(maid,start,demand,motor,null,planning,heading,space,null),true,false);
        reason=result.reason();if(result.source()!=null){selected=result.source();return 2;}return result.waiting()?1:0;
    }
}
