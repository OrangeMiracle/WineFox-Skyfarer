package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Owner-thread source authority. Exclusive equipment excludes lower sources even when its
 * certificate is pending or rejected. Providers still own qualification, proof and execution. */
public final class PropulsionResolver {
    public interface Provider {
        PropulsionDemand.Source source();
        Offer offer(Context context);
        default void warm(Context context) {}
        default boolean sustain(Context context){return false;}
    }
    public record Rule(PropulsionDemand.Source source,int priority,boolean exclusive,
                       java.util.function.Predicate<EntityMaid> equipped) {}
    public static final List<Rule> RULES=List.of(
        new Rule(PropulsionDemand.Source.VIRTUAL_PROPULSION,300,true,CometBaubles::virtual),
        new Rule(PropulsionDemand.Source.RIPTIDE,200,false,m->true),
        new Rule(PropulsionDemand.Source.FIREWORK,100,false,m->true));
    private static final List<Rule> ORDER=RULES.stream().sorted(java.util.Comparator.comparingInt(Rule::priority).reversed()).toList();
    public record Context(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,
        RiptidePropulsion motor,FireworkManager fireworks,AsyncPlanning planning,HeadingController heading,
        GlidePlanner.Corridor space,IntToDoubleFunction rocketCertificate) {}
    public record Offer(boolean accepted,boolean pending,String reason) {
        static Offer rejected(String reason){return new Offer(false,false,reason);}
    }
    public record Result(PropulsionDemand.Source source,boolean waiting,String reason) {}
    private final List<Provider> providers;
    private int waiting;
    private PropulsionDemand.Phase phase;
    public PropulsionResolver(List<Provider> providers){this.providers=List.copyOf(providers);}
    public static PropulsionDemand.Source exclusive(EntityMaid maid){
        return RULES.stream().filter(r->r.exclusive&&r.equipped.test(maid)).max(java.util.Comparator.comparingInt(Rule::priority)).map(Rule::source).orElse(null);
    }
    /** Rechecked by resource commit hooks, including after cancellable mod events. */
    public static boolean permits(EntityMaid maid,PropulsionDemand.Source source){
        if(source==PropulsionDemand.Source.COAST)return true;
        var rule=ORDER.stream().filter(r->r.source==source).findFirst().orElse(null);
        var exclusive=exclusive(maid);return rule!=null&&rule.equipped.test(maid)&&(exclusive==null||exclusive==source);
    }
    public void reset(){waiting=0;phase=null;}
    public void warm(Context context){
        for(var rule:ORDER)if(permits(context.maid,rule.source))
            providers.stream().filter(p->p.source()==rule.source).findFirst().ifPresent(p->p.warm(context));
    }
    public Result sustain(Context context){
        for(var rule:ORDER){
            if(!rule.equipped.test(context.maid)||!permits(context.maid,rule.source))continue;
            var provider=providers.stream().filter(p->p.source()==rule.source).findFirst().orElse(null);
            if(provider!=null&&provider.sustain(context))return new Result(rule.source,false,"CERTIFIED_SPEED_HOLD");
        }
        return new Result(null,false,"SPEED_HOLD_NOT_CERTIFIED");
    }
    public Result resolve(Context context,boolean canWait,boolean includeFirework){
        if(phase!=context.demand.phase()){phase=context.demand.phase();waiting=0;}
        String reason="NO_CERTIFIED_SOURCE";
        for(var rule:ORDER){
            if(!rule.equipped.test(context.maid)||!permits(context.maid,rule.source)||!includeFirework&&rule.source==PropulsionDemand.Source.FIREWORK)continue;
            var provider=providers.stream().filter(p->p.source()==rule.source).findFirst().orElse(null);
            if(provider==null)continue;
            var offer=provider.offer(context);reason=offer.reason;
            if(offer.accepted){waiting=0;return new Result(rule.source,false,reason);}
            if(offer.pending&&canWait&&!context.demand.emergency()&&++waiting<(context.demand.departure()?40:12))return new Result(null,true,reason);
            if(rule.exclusive)return new Result(null,false,reason+";EXCLUSIVE_SOURCE_NO_FALLBACK");
        }
        return new Result(null,false,reason);
    }
}
