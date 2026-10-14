package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class CometLightningMixin {
    @Shadow private int life;
    @Shadow private boolean visualOnly;
    /** Before vanilla spawnFire/rod updates/damage, only on a real bolt's first tick. */
    @Inject(method="tick",at=@At("HEAD"))
    private void wfs$forgeBeforeIgnition(CallbackInfo ci){
        dev.winefox.skyfarer.runtime.CometRitual.strike((LightningBolt)(Object)this,visualOnly,life);
    }
}
