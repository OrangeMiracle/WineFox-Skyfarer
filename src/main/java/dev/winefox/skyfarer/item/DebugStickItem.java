package dev.winefox.skyfarer.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.SkyfarerTask;
import dev.winefox.skyfarer.runtime.DebugWatchManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DebugStickItem extends Item {
    public DebugStickItem(Properties properties) { super(properties); }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer serverPlayer)dev.winefox.skyfarer.runtime.DebugPanelManager.open(serverPlayer);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (!maid.isOwnedBy(player) && !player.hasPermissions(2)) player.displayClientMessage(Component.translatable("message.winefox_skyfarer.not_owner"), true);
            else DebugWatchManager.toggle(serverPlayer, maid);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
