package dev.winefox.skyfarer.mixin.ysm;

import com.elfmcys.yesstevemodel.*;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.winefox.skyfarer.client.YsmFlightCompat;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Custom main controllers run before the maid state selector; give supported glide poses priority. */
@Pseudo
@Mixin(targets="com.elfmcys.yesstevemodel.o0o00o00ooOoOOoo0oOoOOO0",remap=false)
public abstract class YsmMaidAnimationControllerMixin {
    @Shadow @Final private o0OOoOOoOO0Oo00OOoOOO0oo<?> oOoo00O0o0oO0o0oO00OO0O0;
    @WrapOperation(method="oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lcom/elfmcys/yesstevemodel/O00o00OOOo00OOOo0O0oOooo;Z)V",
        at=@At(value="INVOKE",target="Lcom/elfmcys/yesstevemodel/o0o00o00ooOoOOoo0oOoOOO0;oOoo00O0o0oO0o0oO00OO0O0(Lcom/elfmcys/yesstevemodel/O00o00OOOo00OOOo0O0oOooo;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"))
    private O0o0ooO0oOoOoo0O000O0000 wfs$preferGlide(o0o00o00ooOoOOoo0oOoOOO0<?> owner,O00o00OOOo00OOOo0O0oOooo<?> context,
        Operation<O0o0ooO0oOoOoo0O000O0000> original,@Local(argsOnly=true) Oo0Oo0O0OoOoO0oooO00O0o0<?> state) {
        if(oOoo00O0o0oO0o0oO00OO0O0 instanceof oOoO0oOo0OoooOOoO0oO0O00
            && state.oOoo00O0o0oO0o0oO00OO0O0() instanceof OooO0Oo0oO00oo0Ooo0O0O00 model
            && !(model instanceof OoOooOOOoOOOOoo00o00O0OO)) {
            var maid=model.ooo00OoO00OOOO0oOooOo0Oo();
            if(maid!=null && maid.renderState==MaidRenderState.ENTITY && maid.isFallFlying() && !maid.isPassenger()
                && SkyfarerRuntime.isSkyfarer(maid) && YsmFlightCompat.hasFlightAnimation(model)) return null;
        }
        return original.call(owner,context);
    }
}
