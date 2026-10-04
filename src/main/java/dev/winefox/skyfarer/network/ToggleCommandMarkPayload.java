package dev.winefox.skyfarer.network;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Intent only: server raycasts and derives player, held item and privileges itself. */
public record ToggleCommandMarkPayload() implements CustomPacketPayload {
 public static final Type<ToggleCommandMarkPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID,"toggle_command_mark"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ToggleCommandMarkPayload> CODEC=StreamCodec.of((b,p)->{},b->new ToggleCommandMarkPayload());
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
