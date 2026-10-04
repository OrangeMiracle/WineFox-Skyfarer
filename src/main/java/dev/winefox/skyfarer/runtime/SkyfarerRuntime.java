package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.AirCombatController;
import dev.winefox.skyfarer.compat.tlm.SkyfarerTask;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public final class SkyfarerRuntime {
    private static final Map<UUID, AirCombatController> CONTROLLERS = new HashMap<>();
    public static boolean isSkyfarer(EntityMaid maid) { return maid.getTask().getUid().equals(SkyfarerTask.ID); }
    public static AirCombatController get(EntityMaid maid) { return CONTROLLERS.get(maid.getUUID()); }
    public static AirCombatController ensure(EntityMaid maid){return CONTROLLERS.computeIfAbsent(maid.getUUID(),id->new AirCombatController(maid));}
    public static boolean isAirControlActive(EntityMaid maid) { var controller = get(maid); return dev.winefox.skyfarer.command.CommandManager.inTransit(maid) || controller != null && controller.controlsAir(); }
    public static boolean isWaterEscapeActive(EntityMaid maid) {
        var controller=get(maid);
        return dev.winefox.skyfarer.command.CommandManager.inWaterEscape(maid) || controller!=null && controller.controlsAir() && controller.state()==dev.winefox.skyfarer.ai.AirCombatState.WATER_ESCAPE;
    }
    public static void workTick(EntityMaid maid) {
        if(dev.winefox.skyfarer.command.CommandManager.inTransit(maid)||dev.winefox.skyfarer.command.CommandManager.recalling(maid))return;
        if (!maid.level().isClientSide && isSkyfarer(maid)) CONTROLLERS.computeIfAbsent(maid.getUUID(), id -> new AirCombatController(maid)).acquire();
    }
    public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        if(maid.tickCount%20==0)dev.winefox.skyfarer.command.CommandManager.observe(maid);
        if(dev.winefox.skyfarer.command.CommandManager.beforeMaidTick(maid))return;
        var controller = isSkyfarer(maid)
            ? CONTROLLERS.computeIfAbsent(maid.getUUID(), id -> new AirCombatController(maid))
            : get(maid);
        if (controller == null) return;
        controller.tick();
        if (!isSkyfarer(maid) && !controller.controlsAir()) CONTROLLERS.remove(maid.getUUID());
    }
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide) {
            dev.winefox.skyfarer.command.CommandManager.leave(maid);
            var controller = CONTROLLERS.remove(maid.getUUID()); if (controller != null) controller.release();
        }
    }
    public static void onSafetyTeleport(EntityMaid maid) { var controller = get(maid); if (controller != null) controller.release(); }
    public static void clear() { CONTROLLERS.clear(); }
}
