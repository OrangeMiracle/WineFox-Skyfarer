package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.SkyfarerTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.notunanancyowen.spears.components.PiercingWeapon", remap = false)
public abstract class SpearFriendlyFireMixin {
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private static void wfs$respectMaidAllies(Entity attacker, Entity target, CallbackInfoReturnable<Boolean> ci) {
        if (attacker instanceof EntityMaid maid && maid.getTask().getUid().equals(SkyfarerTask.ID)
            && (!(target instanceof LivingEntity living) || target == maid || target == maid.getOwner()
                || target instanceof net.minecraft.world.entity.player.Player || maid.isAlliedTo(target) || !maid.canAttack(living))) ci.setReturnValue(false);
    }
}
