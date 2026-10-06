package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import dev.winefox.skyfarer.command.CommandManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes native/TLM/other-mod flag removal as well as our own; does not override engine eligibility. */
@Mixin(Entity.class)
public abstract class FlightFlagAuditMixin {
    @Inject(method="setSharedFlag",at=@At("HEAD"))
    private void wfs$auditFlightFlag(int flag,boolean value,CallbackInfo ci){
        if(flag!=7||value||!((Object)this instanceof EntityMaid maid)||maid.level().isClientSide||!maid.isFallFlying()||!SkyfarerSettings.debugLogging())return;
        var controller=SkyfarerRuntime.get(maid);
        String source=StackWalker.getInstance().walk(frames->frames.filter(f->!f.getMethodName().contains("wfs$")&&!f.getMethodName().equals("setSharedFlag")).limit(5).map(Object::toString).collect(java.util.stream.Collectors.joining(" <- ")));
        WineFoxSkyfarer.LOGGER.info("[WFS debug] FALL_FLYING_CLEAR maid={} source={} state={} managed={} velocity={} altitude={} ground={} target={} order={}",
            maid.getUUID(),source,controller==null?"none":controller.state(),controller!=null&&controller.controlsAir(),maid.getDeltaMovement(),maid.getY(),maid.onGround(),controller==null?null:controller.debugTarget(),CommandManager.context(maid));
    }
}
