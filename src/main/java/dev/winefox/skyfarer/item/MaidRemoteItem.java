package dev.winefox.skyfarer.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.runtime.RemoteSessions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.List;

public final class MaidRemoteItem extends Item {
    public static final String BINDING = "winefox_maid";
    public MaidRemoteItem(Properties properties) { super(properties); }
    public static CompoundTag binding(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(BINDING);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            if (!maid.isOwnedBy(player)) { player.displayClientMessage(Component.translatable("message.winefox_skyfarer.not_owner"), true); return InteractionResult.FAIL; }
            CompoundTag binding = new CompoundTag();
            binding.putUUID("uuid", maid.getUUID()); binding.putString("name", maid.getName().getString());
            binding.putString("dimension", maid.level().dimension().location().toString());
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(BINDING, binding));
            RemoteSessions.close(player.getUUID());
            player.displayClientMessage(Component.translatable("message.winefox_skyfarer.bound", maid.getName()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) RemoteSessions.open(serverPlayer, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var tag = binding(stack);
        tooltip.add(Component.translatable("tooltip.winefox_skyfarer.remote"));
        if (tag.hasUUID("uuid")) tooltip.add(Component.literal(tag.getString("name") + " · " + tag.getString("dimension")));
    }
}
