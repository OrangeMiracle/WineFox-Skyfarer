package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;

/** One common entry covers Bedrock, Gecko and delegated YSM renderers exactly once. */
@Mixin(value = EntityMaidRenderer.class, remap = false)
public abstract class MaidFlightRenderMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void wfs$flightBody(Mob entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light, Operation<Void> original) {
        if (!(entity instanceof EntityMaid) || !entity.isFallFlying() || entity.getPose() != Pose.FALL_FLYING) {
            original.call(entity, yaw, partial, pose, buffer, light); return;
        }
        pose.pushPose();
        try {
            float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            float ticks = entity.getFallFlyingTicks() + partial;
            float blend = Mth.clamp(ticks * ticks / 100F, 0, 1);
            // Apply in body-local axes before any delegated renderer chooses its model.
            pose.mulPose(Axis.YP.rotationDegrees(180 - body));
            pose.mulPose(Axis.XP.rotationDegrees(blend * (-90 - entity.getViewXRot(partial))));
            pose.mulPose(Axis.YP.rotationDegrees(body - 180));
            original.call(entity, yaw, partial, pose, buffer, light);
        } finally { pose.popPose(); }
    }
}
