package dev.winefox.skyfarer.flight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.SkyfarerEquipment;
import dev.winefox.skyfarer.mixin.EntityFlightAccessor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;

public final class ElytraFlightController {
    public static boolean startGliding(EntityMaid maid) {
        return startGliding(maid,false);
    }
    public static boolean startGliding(EntityMaid maid,boolean waterEscape) {
        // LivingEntity.updateFallFlying permits wet non-player gliders once they leave the floor.
        if (maid.onGround() || (!waterEscape && maid.isInWaterOrBubble()) || maid.isPassenger() || maid.hasEffect(MobEffects.LEVITATION) || !SkyfarerEquipment.elytra(maid)) return false;
        ((EntityFlightAccessor)maid).wfs$setSharedFlag(7, true);
        maid.setPose(Pose.FALL_FLYING);
        return maid.isFallFlying();
    }
    public static void stopGliding(EntityMaid maid) {
        ((EntityFlightAccessor)maid).wfs$setSharedFlag(7, false);
        if (maid.getPose() == Pose.FALL_FLYING) maid.setPose(Pose.STANDING);
    }
}
