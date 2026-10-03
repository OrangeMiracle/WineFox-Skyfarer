package dev.winefox.skyfarer.network;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;

public record OpenRemoteTaskPayload(UUID session, UUID maid, String name, ResourceLocation current, List<TaskOption> tasks) implements CustomPacketPayload {
    public record TaskOption(ResourceLocation id, boolean enabled) {}
    public static final Type<OpenRemoteTaskPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID, "open_remote"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRemoteTaskPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.session); b.writeUUID(p.maid); b.writeUtf(p.name, 256); b.writeResourceLocation(p.current);
        b.writeCollection(p.tasks, (buf, task) -> { buf.writeResourceLocation(task.id); buf.writeBoolean(task.enabled); });
    }, b -> new OpenRemoteTaskPayload(b.readUUID(), b.readUUID(), b.readUtf(256), b.readResourceLocation(), b.readCollection(net.minecraft.network.FriendlyByteBuf.limitValue(java.util.ArrayList::new, 512), buf -> new TaskOption(buf.readResourceLocation(), buf.readBoolean()))));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
