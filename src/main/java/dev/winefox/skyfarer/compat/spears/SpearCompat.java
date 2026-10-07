package dev.winefox.skyfarer.compat.spears;

import com.notunanancyowen.spears.Spears;
import com.notunanancyowen.spears.components.KineticWeapon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Delegates every hit, relative-speed check and cooldown to Spears 1.8.0. */
public final class SpearCompat {
    private SpearCompat() {}
    public static boolean isChargeWeapon(ItemStack stack) {
        KineticWeapon weapon = Spears.KINETIC_WEAPON == null ? null : stack.get(Spears.KINETIC_WEAPON);
        return weapon != null && weapon.damageConditions().isPresent() && weapon.damageMultiplier() > 0;
    }
    public static int delayTicks(ItemStack stack) {
        KineticWeapon weapon = stack.get(Spears.KINETIC_WEAPON);
        return weapon == null ? 0 : weapon.delayTicks();
    }
    public static double mobReach(ItemStack stack) {
        var range = stack.get(Spears.ATTACK_RANGE);
        return (range == null ? 3 : range.maxReach()) * 0.5;
    }
    public static double mobMinReach(ItemStack stack) {
        var range = stack.get(Spears.ATTACK_RANGE);
        return range == null ? 0 : range.minReach() * 0.5;
    }
    public static double hitboxMargin(ItemStack stack) {
        var weapon = stack.get(Spears.KINETIC_WEAPON);
        return weapon == null ? 0 : weapon.hitboxMargin();
    }
    public static int chargeWindow(ItemStack stack) {
        var weapon = stack.get(Spears.KINETIC_WEAPON);
        return weapon == null ? 0 : weapon.getUseTicks();
    }
    public static double minimumDamageSpeed(ItemStack stack) {
        var weapon=stack.get(Spears.KINETIC_WEAPON);
        // Spears measures blocks/second and multiplies its thresholds by 0.2 for mobs.
        return weapon==null?0:weapon.damageConditions().map(c->Math.max(c.minSpeed(),c.minRelativeSpeed())*.01).orElse(0.0);
    }
    public static void startCharge(LivingEntity maid) {
        if (isChargeWeapon(maid.getMainHandItem()) && !maid.isUsingItem()) maid.startUsingItem(InteractionHand.MAIN_HAND);
    }
    public static void stopCharge(LivingEntity maid) {
        if (maid.isUsingItem() && isChargeWeapon(maid.getUseItem())) maid.stopUsingItem();
    }
    /** PiercingWeapon.stab uses the ordinary attack-range component, NOT KineticWeapon's mob half-range. */
    public static double groundMinimum(ItemStack stack) {
        var range=stack.get(Spears.ATTACK_RANGE);return range==null?0:range.minReach();
    }
    /** Shared by the pre-swing check and the actual native PiercingWeapon.stab collector. */
    public static float groundMaximum(LivingEntity user,float nativeMaximum) {
        if(user instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
            &&!maid.level().isClientSide&&maid.onGround()&&!maid.isFallFlying()
            &&maid.getTask().getUid().equals(dev.winefox.skyfarer.compat.tlm.SkyfarerTask.ID)) {
            return (float)GroundMeleeReach.maximum(nativeMaximum,
                maid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE),
                maid.getFavorabilityManager().getAttackDistancePlusByPoint(maid.getFavorability()));
        }
        return nativeMaximum;
    }
    public static double groundMaximum(LivingEntity user) {
        var range=user.getMainHandItem().get(Spears.ATTACK_RANGE);
        return GroundMeleeReach.stoppingMaximum(groundMaximum(user,range==null?3:range.maxReach()));
    }
    public static boolean groundContact(LivingEntity maid,LivingEntity target) {
        var stack=maid.getMainHandItem();var range=stack.get(Spears.ATTACK_RANGE);
        float min=range==null?0:range.minReach();
        double max=groundMaximum(maid);
        var direction=maid.calculateViewVector(maid.getXRot(),maid.getYHeadRot());
        if(!GroundMeleeReach.contact(maid.getEyePosition(),direction,target.getBoundingBox(),min,max))return false;
        var piercing=stack.get(Spears.PIERCING_WEAPON);
        if(piercing==null)return maid.hasLineOfSight(target);
        // Cancel the collector's movement extension: stopping must leave a stationary attack in range.
        float queryMax=(float)(max-Math.max(0,maid.getKnownMovement().dot(direction)));
        if(queryMax<min)return false;
        return Spears.collectPiercingCollisions(maid,min,queryMax,0,
            e->e==target&&com.notunanancyowen.spears.components.PiercingWeapon.canHit(maid,e)).stream().anyMatch(hit->hit.getEntity()==target);
    }
}
