package dev.winefox.skyfarer.mixin;

import dev.winefox.skyfarer.runtime.CometRelicDrops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only color selection for the native outline buffer; no scoreboard teams. */
@Mixin(Entity.class)
public abstract class CometOutlineMixin {
    @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true)
    private void wfs$relicColor(CallbackInfoReturnable<Integer> ci){
        if((Object)this instanceof ItemEntity item){
            int color=CometRelicDrops.color(item.getItem());if(color!=-1)ci.setReturnValue(color);
        }
    }
}
