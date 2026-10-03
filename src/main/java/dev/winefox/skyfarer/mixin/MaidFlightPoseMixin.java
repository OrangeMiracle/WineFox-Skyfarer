package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidSwimManager;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidSwimManager.class, remap = false)
public abstract class MaidFlightPoseMixin {
    @Shadow @Final private EntityMaid maid;
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void wfs$preserveGlide(CallbackInfo ci) {
        if (maid.isFallFlying() && !maid.onGround() && SkyfarerRuntime.isAirControlActive(maid)
            && (!maid.isInWaterOrBubble() || SkyfarerRuntime.isWaterEscapeActive(maid))) {
            maid.setSwimming(false);
            maid.setPose(Pose.FALL_FLYING);
            ci.cancel();
        }
    }
}
