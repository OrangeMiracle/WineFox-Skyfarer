package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.notunanancyowen.spears.components.KineticWeapon", remap = false)
public abstract class SpearMovementMixin {
    @Inject(method = "getAmplifiedMovement", at = @At("HEAD"), cancellable = true)
    private static void wfs$completedTickMovement(Entity entity, CallbackInfoReturnable<Vec3> ci) {
        if (entity instanceof EntityMaid maid && !maid.level().isClientSide) {
            var controller = SkyfarerRuntime.get(maid);
            // ServerLevel resets xo before LivingEntity updates item use, before travel.
            // Supply measured movement, never a synthetic attack speed or damage value.
            if (controller != null && controller.controlsAir()) ci.setReturnValue(controller.measuredMovement().scale(20));
        }
    }
}
