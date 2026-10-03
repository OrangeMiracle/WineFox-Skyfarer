package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask",
    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask"
}, remap = false)
public abstract class TlmFollowOwnerMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z", at = @At("HEAD"), cancellable = true)
    private void wfs$skipFollow(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> ci) {
        if (SkyfarerRuntime.isAirControlActive(maid)) ci.setReturnValue(false);
    }
    @Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V", at = @At("HEAD"), cancellable = true)
    private void wfs$skipExistingFollow(ServerLevel level, EntityMaid maid, long time, CallbackInfo ci) {
        if (SkyfarerRuntime.isAirControlActive(maid)) ci.cancel();
    }
}
