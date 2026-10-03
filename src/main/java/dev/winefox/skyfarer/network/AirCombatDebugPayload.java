package dev.winefox.skyfarer.network;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-produced snapshot; an empty tag clears the client's watch immediately. */
public record AirCombatDebugPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<AirCombatDebugPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID, "air_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AirCombatDebugPayload> CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> {
        CompoundTag tag = b.readNbt(); return new AirCombatDebugPayload(tag == null ? new CompoundTag() : tag);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
