package dev.winefox.skyfarer.compat.spears;
import net.minecraft.world.item.ItemStack;import net.minecraft.world.entity.LivingEntity;
/** External weapon API fixture. No targeting or state decisions live here. */
public class SpearCompat {
 public static int windup=10;
 public static boolean isChargeWeapon(ItemStack s){return s.spear&&s.count>0;}public static int delayTicks(ItemStack s){return windup;}
 public static double mobReach(ItemStack s){return 2.25;}public static double mobMinReach(ItemStack s){return 0;}public static double hitboxMargin(ItemStack s){return .1;}
 public static int chargeWindow(ItemStack s){return 200;}public static double minimumDamageSpeed(ItemStack s){return .2;}
 public static void startCharge(LivingEntity e){if(!e.using){e.using=true;e.usedTicks=0;}}public static void stopCharge(LivingEntity e){e.using=false;e.usedTicks=0;}
}
