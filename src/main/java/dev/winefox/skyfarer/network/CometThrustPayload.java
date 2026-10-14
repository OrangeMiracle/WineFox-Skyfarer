package dev.winefox.skyfarer.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record CometThrustPayload(int entity,UUID identity,int power,boolean ring) implements CustomPacketPayload {
    public static final Type<CometThrustPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("winefox_skyfarer","comet_thrust"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CometThrustPayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.entity);b.writeUUID(p.identity);b.writeByte(p.power);b.writeBoolean(p.ring);},
        b->new CometThrustPayload(b.readVarInt(),b.readUUID(),b.readUnsignedByte(),b.readBoolean()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
