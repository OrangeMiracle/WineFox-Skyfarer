package dev.winefox.skyfarer.mixin.ysm;

import com.elfmcys.yesstevemodel.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.winefox.skyfarer.client.YsmFlightCompat;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.elfmcys.yesstevemodel.ooOoooo0o000Oo0OoOooo0oo",remap=false)
public abstract class YsmMaidRendererMixin {
    @Inject(method="oOo0OO0O0o000OO0O000oo0o(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("HEAD"))
    private void wfs$nativeBody(EntityMaid maid,PoseStack pose,float age,float yaw,float partial,float scale,CallbackInfo ci) {
        // Here the selected model and its animations are already loaded, including on its first frame.
        var model=oO0o00oOOooOO0OOooOOooOo.oOo0OO0O0o000OO0O000oo0o(maid).orElse(null);
        if(!maid.isFallFlying() || maid.getPose()!=Pose.FALL_FLYING || maid.renderState!=MaidRenderState.ENTITY
            || model==null || !SkyfarerRuntime.isSkyfarer(maid) || !YsmFlightCompat.hasFlightAnimation(model)) return;
        // Native elytra_fly already rotates Root 90 degrees. Undo the outer fallback exactly once.
        float body=Mth.rotLerp(partial,maid.yBodyRotO,maid.yBodyRot);
        float ticks=maid.getFallFlyingTicks()+partial;
        float angle=Mth.clamp(ticks*ticks/100F,0,1)*(-90-maid.getViewXRot(partial));
        pose.mulPose(Axis.YP.rotationDegrees(180-body));
        pose.mulPose(Axis.XP.rotationDegrees(-angle));
        pose.mulPose(Axis.YP.rotationDegrees(body-180));
    }
    @Inject(method="oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/OooO0Oo0oO00oo0Ooo0O0O00;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lcom/elfmcys/yesstevemodel/OoooO0Ooo0O0o0OO0ooOOooO;)V",at=@At("TAIL"))
    private void wfs$elytraLayer(OooO0Oo0oO00oo0Ooo0O0O00 model,float partial,PoseStack pose,MultiBufferSource buffer,int light,
                                 Oo0Oo0O0OoOoO0oooO00O0o0<?> controller,OoooO0Ooo0O0o0OO0ooOOooO context,CallbackInfo ci) {
        if(SkyfarerRuntime.isSkyfarer(model.ooo00OoO00OOOO0oOooOo0Oo())) YsmFlightCompat.renderWings(model,pose,buffer,light);
    }
}
