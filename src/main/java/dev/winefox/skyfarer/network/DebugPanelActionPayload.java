package dev.winefox.skyfarer.network;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DebugPanelActionPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<DebugPanelActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID,"debug_panel_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DebugPanelActionPayload> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->{var tag=b.readNbt();return new DebugPanelActionPayload(tag==null?new CompoundTag():tag);});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
