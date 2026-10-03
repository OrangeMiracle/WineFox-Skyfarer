package dev.winefox.skyfarer.network;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record SetRemoteMaidTaskPayload(UUID session, ResourceLocation task) implements CustomPacketPayload {
    public static final Type<SetRemoteMaidTaskPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID, "set_remote_task"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetRemoteMaidTaskPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.session); b.writeResourceLocation(p.task);
    }, b -> new SetRemoteMaidTaskPayload(b.readUUID(), b.readResourceLocation()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
