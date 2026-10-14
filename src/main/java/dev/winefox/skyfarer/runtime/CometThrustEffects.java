package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.ThrustEffectGate;
import dev.winefox.skyfarer.network.CometThrustPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread weak lifecycle state; one small tracking packet, not server particle entities. */
@EventBusSubscriber(modid="winefox_skyfarer")
public final class CometThrustEffects {
    private static final java.util.Map<EntityMaid,ThrustEffectGate> gates=new java.util.WeakHashMap<>();
    private static long packets,rings,stops,lastReport;
    public static void emit(EntityMaid maid,float force){
        if(maid.level().isClientSide)return;
        send(maid,gates.computeIfAbsent(maid,m->new ThrustEffectGate()).actual(maid.level().getGameTime(),force));
    }
    private static void send(EntityMaid maid,ThrustEffectGate.Frame frame){
        if(frame!=null){
            PacketDistributor.sendToPlayersTrackingEntity(maid,new CometThrustPayload(maid.getId(),maid.getUUID(),frame.power(),frame.ring()));
            packets++;if(frame.ring())rings++;if(frame.power()==0)stops++;
            long now=maid.level().getGameTime();
            if(now-lastReport>=100&&dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging()){
                lastReport=now;dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS comet-fx server] tx={} rings={} stops={} currentPower={}",packets,rings,stops,frame.power());
            }
        }
    }
    @SubscribeEvent public static void after(EntityTickEvent.Post event){
        if(event.getEntity() instanceof EntityMaid maid&&!maid.level().isClientSide){var gate=gates.get(maid);if(gate!=null)send(maid,gate.finish(maid.level().getGameTime()));}
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){gates.clear();packets=rings=stops=lastReport=0;}
}
