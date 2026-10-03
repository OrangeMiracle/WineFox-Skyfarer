package dev.winefox.skyfarer.mixin.ysm;

import com.elfmcys.yesstevemodel.O0oOOO0o0ooo0OO00000o0Oo;
import dev.winefox.skyfarer.client.YsmFlightCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.elfmcys.yesstevemodel.Oo000O00O0OOoO00OOOO000O",remap=false)
public abstract class YsmElytraQueriesMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void wfs$maidWingQueries(CallbackInfo ci) {
        var queries=(O0oOOO0o0ooo0OO00000o0Oo)(Object)this;
        queries.OO000o0ooOooooOOOOO0Ooo0("elytra_rot_x",ctx->YsmFlightCompat.rotation(ctx.oOo0OO0O0o000OO0O000oo0o(),0));
        queries.OO000o0ooOooooOOOOO0Ooo0("elytra_rot_y",ctx->YsmFlightCompat.rotation(ctx.oOo0OO0O0o000OO0O000oo0o(),1));
        queries.OO000o0ooOooooOOOOO0Ooo0("elytra_rot_z",ctx->YsmFlightCompat.rotation(ctx.oOo0OO0O0o000OO0O000oo0o(),2));
    }
}
