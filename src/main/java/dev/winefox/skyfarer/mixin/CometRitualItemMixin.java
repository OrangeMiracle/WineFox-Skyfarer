package dev.winefox.skyfarer.mixin;

import dev.winefox.skyfarer.runtime.CometRitual;
import dev.winefox.skyfarer.runtime.CometRelicDrops;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class CometRitualItemMixin {
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void wfs$protectRelicsAndRitualRemainder(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        if(CometRelicDrops.protectedFrom((ItemEntity)(Object)this,source))ci.setReturnValue(false);
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void wfs$refreshRelic(CallbackInfo ci){CometRelicDrops.refresh((ItemEntity)(Object)this);}
    @Inject(method="isMergable",at=@At("HEAD"),cancellable=true)
    private void wfs$preserveTemporaryProtection(CallbackInfoReturnable<Boolean> ci){
        if(CometRitual.protectedItem((ItemEntity)(Object)this))ci.setReturnValue(false);
    }
}
