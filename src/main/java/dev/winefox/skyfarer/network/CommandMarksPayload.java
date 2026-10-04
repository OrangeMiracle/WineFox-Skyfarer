package dev.winefox.skyfarer.network;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CommandMarksPayload(CompoundTag data) implements CustomPacketPayload {
 public static final Type<CommandMarksPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID,"command_marks"));
 public static final StreamCodec<RegistryFriendlyByteBuf,CommandMarksPayload> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new CommandMarksPayload(b.readNbt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
