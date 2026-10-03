package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidHeldItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.notunanancyowen.spears.Spears;
import com.notunanancyowen.spears.SpearsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TLM bypasses vanilla ItemInHandLayer, so bridge Spears' charge item transform here. */
@Mixin(value = LayerMaidHeldItem.class, remap = false)
public abstract class TlmSpearItemPoseMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void wfs$chargePose(Mob maid, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
        PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!maid.isFallFlying() || !maid.isUsingItem() || maid.getUseItem() != stack) return;
        var kinetic = stack.get(Spears.KINETIC_WEAPON);
        if (kinetic == null) return;
        float ticks = maid.getTicksUsingItem() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var animation = SpearsClient.holdUpAnimation.play(kinetic, ticks);
        int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        float phase = -animation.raiseProgress();
        float eased = -(2.70158F * phase * phase * phase + 1.70158F * phase * phase);
        pose.translate(0, 0, -kinetic.forwardMovement() * (eased - animation.raiseBackProgress()));
        pose.rotateAround(Axis.XN.rotationDegrees((animation.raiseProgress() - animation.raiseBackProgress()) * 70), 0, -0.03125F, 0.125F);
        pose.rotateAround(Axis.YP.rotationDegrees((animation.raiseProgress() - animation.swayProgress()) * sign * 90), 0, 0, 0.125F);
    }
}
