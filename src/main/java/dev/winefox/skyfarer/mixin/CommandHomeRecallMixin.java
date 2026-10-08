package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import dev.winefox.skyfarer.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep live dispatch and managed airborne recovery from being interrupted by Home scheduling. */
@Mixin(value=SchedulePos.class,remap=false)
public abstract class CommandHomeRecallMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void wfs$holdDispatch(EntityMaid maid,CallbackInfo ci) {
        boolean held=CommandManager.holdHome(maid);
        CommandManager.homeScheduleOpportunity(maid,held);
        if(held)ci.cancel();
    }
}
