package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.TlmFollowCompat;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class TlmAirControlMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void wfs$glidingDimensions(net.minecraft.world.entity.Pose pose, CallbackInfoReturnable<net.minecraft.world.entity.EntityDimensions> ci) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (pose == net.minecraft.world.entity.Pose.FALL_FLYING)
            ci.setReturnValue(net.minecraft.world.entity.EntityDimensions.scalable(maid.getType().getDimensions().width(), 0.6F).withEyeHeight(0.4F));
    }
    @Inject(method = "canBrainMoving", at = @At("HEAD"), cancellable = true)
    private void wfs$noGroundRequests(CallbackInfoReturnable<Boolean> ci) {
        if (SkyfarerRuntime.isAirControlActive((EntityMaid)(Object)this)) ci.setReturnValue(false);
    }
    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void wfs$clearGroundMemories(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid)(Object)this;
        if (SkyfarerRuntime.isAirControlActive(maid)) TlmFollowCompat.clearGroundMovement(maid);
    }
    // Do not cancel teleportToOwner: TLM's chunk-unload failsafe must keep working.
    @Inject(method = "teleportToOwner", at = @At("RETURN"))
    private void wfs$externalSafetyTeleport(LivingEntity owner, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) SkyfarerRuntime.onSafetyTeleport((EntityMaid)(Object)this);
    }
}
