package dev.winefox.skyfarer.client;

import com.elfmcys.yesstevemodel.OooO0Oo0oO00oo0Ooo0O0O00;
import com.elfmcys.yesstevemodel.oOOOOOOO0ooOo0OoOOO0ooOO;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Loaded only by the optional YSM client mixins, using the inspected 2.6.5 model interfaces. */
public final class YsmFlightCompat {
    private YsmFlightCompat() {}
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
    private static final ModelPart ROOT=ElytraModel.createLayer().bakeRoot();
    private static final ElytraModel<LivingEntity> WINGS=new ElytraModel<>(ROOT);
    public static boolean hasFlightAnimation(OooO0Oo0oO00oo0Ooo0O0O00 model) {
        var animation=model.Oo0O0OoOo0O0oOoo0000O0oO("elytra_fly");
        return animation!=null && !animation.oOo0OO0O0o000OO0O000oo0o();
    }
    public static double rotation(LivingEntity entity,int axis) {
        // Preserve YSM's existing smoothed player queries without ticking them an extra time.
        if(entity instanceof AbstractClientPlayer p)
            return Math.toDegrees(axis==0?p.elytraRotX:axis==1?p.elytraRotY:p.elytraRotZ);
        if(!(entity instanceof EntityMaid)) return 0;
        WINGS.setupAnim(entity,0,0,entity.tickCount,0,0);
        var wing=ROOT.getChild("left_wing");
        return Math.toDegrees(axis==0?wing.xRot:axis==1?wing.yRot:wing.zRot);
    }
    public static void renderWings(OooO0Oo0oO00oo0Ooo0O0O00 model,PoseStack pose,MultiBufferSource buffers,int light) {
        var maid=model.ooo00OoO00OOOO0oOooOo0Oo();
        var stack=maid.getItemBySlot(EquipmentSlot.CHEST);
        var bones=model.O00OOOo00Oo0OO0000oOo0oo();
        if(stack.isEmpty() || !stack.canElytraFly(maid) || maid.isInvisible() || bones==null
            || bones.OOo0O00Ooo00O0Ooo0OoOo0o().isEmpty()) return;
        pose.pushPose();
        try {
            // The same ElytraLocator bone chain and transforms used by YSM's player layer.
            if(oOOOOOOO0ooOo0OoOOO0ooOO.oOo0OO0O0o000OO0O000oo0o(pose,bones.OOo0O00Ooo00O0Ooo0OoOo0o())) return;
            pose.translate(0,1.5,0); pose.mulPose(Axis.ZP.rotationDegrees(180)); pose.scale(2,2,2);
            WINGS.setupAnim(maid,0,0,maid.tickCount,0,0);
            WINGS.renderToBuffer(pose,ItemRenderer.getArmorFoilBuffer(buffers,RenderType.armorCutoutNoCull(TEXTURE),stack.hasFoil()),
                light,OverlayTexture.NO_OVERLAY,-1);
        } finally { pose.popPose(); }
    }
}
