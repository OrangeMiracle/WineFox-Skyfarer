package dev.winefox.skyfarer.mixin.ysm;

import com.elfmcys.yesstevemodel.*;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import dev.winefox.skyfarer.client.YsmFlightCompat;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.elfmcys.yesstevemodel.oOoO0oOo0OoooOOoO0oO0O00",remap=false)
public abstract class YsmMaidAnimationMixin {
    @Inject(method="oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lcom/elfmcys/yesstevemodel/O00o00OOOo00OOOo0O0oOooo;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;",at=@At("HEAD"),cancellable=true)
    private void wfs$elytraAnimation(Oo0Oo0O0OoOoO0oooO00O0o0<OooO0Oo0oO00oo0Ooo0O0O00> controller,
                                     O00o00OOOo00OOOo0O0oOooo<?> context,CallbackInfoReturnable<O0o0ooO0oOoOoo0O000O0000> ci) {
        var model=controller.oOoo00O0o0oO0o0oO00OO0O0();
        var maid=model.ooo00OoO00OOOO0oOooOo0Oo();
        if(maid==null || model instanceof OoOooOOOoOOOOoo00o00O0OO || maid.renderState!=MaidRenderState.ENTITY
            || !SkyfarerRuntime.isSkyfarer(maid) || !maid.isFallFlying() || maid.isPassenger() || !YsmFlightCompat.hasFlightAnimation(model)) return;
        ci.setReturnValue(o0OOoOOoOO0Oo00OOoOOO0oo.oOoo00O0o0oO0o0oO00OO0O0(controller,"elytra_fly"));
    }
}
