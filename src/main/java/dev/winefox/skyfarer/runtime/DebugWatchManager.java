package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.AirCombatState;
import dev.winefox.skyfarer.ai.AirRoute;
import dev.winefox.skyfarer.compat.tlm.SkyfarerTask;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.network.AirCombatDebugPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DebugWatchManager {
    private static final Map<UUID, UUID> WATCHES = new HashMap<>();
    public static void toggle(ServerPlayer player, EntityMaid maid) {
        if (maid.getUUID().equals(WATCHES.get(player.getUUID()))) {
            clear(player); player.displayClientMessage(Component.translatable("message.winefox_skyfarer.debug_off"), true);
        } else {
            WATCHES.put(player.getUUID(), maid.getUUID()); send(player, maid);
            player.displayClientMessage(Component.translatable("message.winefox_skyfarer.debug_on", maid.getName()), true);
        }
    }
    public static void clear(ServerPlayer player) { WATCHES.remove(player.getUUID()); PacketDistributor.sendToPlayer(player, new AirCombatDebugPayload(new CompoundTag())); }
    public static void logout(UUID player) { WATCHES.remove(player); }
    public static void clearAll() { WATCHES.clear(); }
    public static void tick(ServerTickEvent.Post event) {
        var iterator = WATCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) { iterator.remove(); continue; }
            var entity = player.serverLevel().getEntity(entry.getValue());
            if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !maid.getTask().getUid().equals(SkyfarerTask.ID) || (!maid.isOwnedBy(player) && !player.hasPermissions(2))) {
                iterator.remove(); PacketDistributor.sendToPlayer(player, new AirCombatDebugPayload(new CompoundTag())); continue;
            }
            if (player.serverLevel().getGameTime() % SkyfarerSettings.ticks("sync_interval_ticks") == 0) send(player, maid);
        }
    }
    private static void send(ServerPlayer player, EntityMaid maid) {
        var controller = SkyfarerRuntime.get(maid);
        var transit=dev.winefox.skyfarer.command.CommandManager.debug(maid);
        if(transit!=null){PacketDistributor.sendToPlayer(player,new AirCombatDebugPayload(transit));return;}
        PacketDistributor.sendToPlayer(player, new AirCombatDebugPayload(controller == null ? DebugSnapshot.make(maid, AirCombatState.GROUNDED, null, null, null, 0, maid.getLookAngle(), 0, AirRoute.EMPTY) : controller.debugSnapshot()));
    }
}
