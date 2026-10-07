package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe external item-use cancellation without blocking vanilla or another mod. */
@Mixin(LivingEntity.class)
public abstract class RiptideUseAuditMixin {
    @Inject(method="stopUsingItem",at=@At("HEAD"))
    private void wfs$auditStop(CallbackInfo ci) {
        if((Object)this instanceof EntityMaid maid&&!maid.level().isClientSide&&SkyfarerSettings.debugLogging()) {
            var controller=SkyfarerRuntime.get(maid);
            if(controller!=null&&controller.riptideActive())controller.riptideUseStopped(
                StackWalker.getInstance().walk(frames->frames.skip(1).limit(5).map(f->f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber()).toList()).toString());
        }
    }
}
