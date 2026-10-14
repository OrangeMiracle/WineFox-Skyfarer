package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CometDeathMixin {
    @Shadow public boolean dead;
    @Inject(method="die",at=@At("TAIL"))
    private void wfs$confirmedDeath(DamageSource source,CallbackInfo ci){
        if(dead)dev.winefox.skyfarer.runtime.CometEvolution.killed((LivingEntity)(Object)this,source);
    }
}
