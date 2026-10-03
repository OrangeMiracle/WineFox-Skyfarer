package dev.winefox.skyfarer.client;

import com.github.tartaricacid.touhoulittlemaid.api.animation.ICustomAnimation;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IModelRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import net.minecraft.world.entity.Mob;
import java.util.HashMap;
import com.notunanancyowen.spears.Spears;
import com.notunanancyowen.spears.SpearsClient;
import net.minecraft.client.Minecraft;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

/** TLM's public animation hook runs after model scripts; no replacement renderer. */
public final class SkyfarerAnimation implements ICustomAnimation<Mob> {
    // Body orientation is applied once at the common renderer entry, including YSM.
    @Override public void setRotationAngles(Mob mob, HashMap<String, ? extends IModelRenderer> models,
        float swing, float amount, float age, float yaw, float pitch) {
        if (!(mob instanceof EntityMaid) || !mob.isFallFlying()) return;
        boolean charging = mob.isUsingItem() && SpearCompat.isChargeWeapon(mob.getUseItem());
        for (String name : new String[]{"armRight", "armLeft", "legRight", "legLeft"}) {
            var part = ICustomAnimation.getPartOrNull(models, name);
            if (part == null) continue;
            part.xRot = 0; part.yRot = 0;
            // TLM's Bedrock held-item layer always mounts MAIN_HAND on armRight.
            boolean weaponArm = name.equals("armRight");
            part.zRot = name.startsWith("arm") ? (charging && weaponArm ? 0 : name.equals("armRight") ? 0.12F : -0.12F) : 0;
            if (charging && weaponArm) part.xRot = chargeArmPitch(mob);
        }
        var head = ICustomAnimation.getPartOrNull(models, "head");
        if (head != null) head.xRot = -(float)Math.PI / 4;
    }
    @Override public void setGeckoRotationAngles(Mob mob, AnimatedGeoModel model, float swing, float amount, float age, float yaw, float pitch) {
        if (!(mob instanceof EntityMaid) || !mob.isFallFlying()) return;
        boolean charge = mob.isUsingItem() && SpearCompat.isChargeWeapon(mob.getUseItem());
        if (model.rightArm() != null) model.rightArm().setRotation(charge ? -chargeArmPitch(mob) : 0, 0, -0.12F);
        if (model.leftArm() != null) model.leftArm().setRotation(0, 0, 0.12F);
        if (model.head() != null) model.head().setRotationX((float)Math.PI / 4);
    }
    private static float chargeArmPitch(Mob mob) {
        var kinetic = mob.getUseItem().get(Spears.KINETIC_WEAPON);
        float ticks = mob.getTicksUsingItem() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var animation = SpearsClient.holdUpAnimation.play(kinetic, ticks);
        // Same charge phases as Spears' HumanoidModel hook, adapted to TLM bones.
        float raise = -40 * animation.raiseProgressStart() + 30 * animation.raiseProgressMiddle()
            + 20 * animation.raiseProgressEnd() - 20 * animation.lowerProgress() + 10 * animation.raiseBackProgress();
        return -1.5707964F - 0.7853982F + 0.8F - 0.9599311F + (float)Math.toRadians(raise);
    }
}
