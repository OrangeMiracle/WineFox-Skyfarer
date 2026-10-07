package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.RiptidePropulsion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's release only handles Player. Adapt certified maid releases at the same entry point. */
@Mixin(TridentItem.class)
public abstract class MaidRiptideReleaseMixin {
    @Inject(method="releaseUsing",at=@At("HEAD"))
    private void wfs$release(ItemStack stack,Level level,LivingEntity entity,int remaining,CallbackInfo ci) {
        if(!level.isClientSide&&entity instanceof EntityMaid maid)RiptidePropulsion.releaseAuthorized(maid,stack,remaining);
    }
}
