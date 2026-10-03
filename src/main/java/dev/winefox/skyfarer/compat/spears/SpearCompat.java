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
}
