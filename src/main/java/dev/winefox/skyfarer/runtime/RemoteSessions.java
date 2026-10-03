package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.item.MaidRemoteItem;
import dev.winefox.skyfarer.network.OpenRemoteTaskPayload;
import dev.winefox.skyfarer.network.SetRemoteMaidTaskPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RemoteSessions {
    private record Session(UUID nonce, UUID maid, ResourceLocation dimension, long expires, long lastRequest) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private RemoteSessions() {}
    public static void close(UUID player) { SESSIONS.remove(player); }
    public static void clear() { SESSIONS.clear(); }
    public static void open(ServerPlayer player, ItemStack stack) {
        var tag = MaidRemoteItem.binding(stack);
        if (!tag.hasUUID("uuid")) { fail(player, "not_bound"); return; }
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null || !dimension.equals(player.level().dimension().location())) { fail(player, "wrong_dimension"); return; }
        var entity = player.serverLevel().getEntity(tag.getUUID("uuid"));
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || maid.isRemoved()) { fail(player, "unavailable"); return; }
        if (!maid.isOwnedBy(player)) { fail(player, "not_owner"); return; }
        Session session = new Session(UUID.randomUUID(), maid.getUUID(), dimension, player.serverLevel().getGameTime() + 1200, -1);
        SESSIONS.put(player.getUUID(), session); send(player, maid, session);
    }
    private static boolean holdsBinding(ServerPlayer player, Session session) {
        for (ItemStack stack : java.util.List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (!stack.is(WineFoxSkyfarer.MAID_REMOTE.get())) continue;
            var tag = MaidRemoteItem.binding(stack);
            if (tag.hasUUID("uuid") && tag.getUUID("uuid").equals(session.maid) && tag.getString("dimension").equals(session.dimension.toString())) return true;
        }
        return false;
    }
    public static void setTask(ServerPlayer player, SetRemoteMaidTaskPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        if (session == null || !session.nonce.equals(payload.session()) || now > session.expires || !holdsBinding(player, session)
            || !session.dimension.equals(player.level().dimension().location())) { close(player.getUUID()); fail(player, "expired"); return; }
        if (session.lastRequest >= 0 && now - session.lastRequest < 4) return;
        var entity = player.serverLevel().getEntity(session.maid);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || maid.isRemoved() || !maid.isOwnedBy(player)) { close(player.getUUID()); fail(player, "unavailable"); return; }
        var task = TaskManager.findTask(payload.task()).orElse(null);
        if (task == null || task.isHidden(maid) || !task.isEnable(maid)) { fail(player, "task_disabled"); send(player, maid, session); return; }
        maid.setTask(task);
        Session renewed = new Session(session.nonce, session.maid, session.dimension, now + 1200, now);
        SESSIONS.put(player.getUUID(), renewed); send(player, maid, renewed);
    }
    private static void send(ServerPlayer player, EntityMaid maid, Session session) {
        var tasks = TaskManager.getNotHiddenTaskList(maid).stream().limit(512)
            .map(task -> new OpenRemoteTaskPayload.TaskOption(task.getUid(), task.isEnable(maid))).toList();
        PacketDistributor.sendToPlayer(player, new OpenRemoteTaskPayload(session.nonce, maid.getUUID(), maid.getName().getString(), maid.getTask().getUid(), tasks));
    }
    private static void fail(ServerPlayer player, String message) { player.displayClientMessage(Component.translatable("message.winefox_skyfarer." + message), false); }
}
