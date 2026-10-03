package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public final class SkyfarerEquipment {
    private SkyfarerEquipment() {}
    public static boolean spear(EntityMaid maid) { return SpearCompat.isChargeWeapon(maid.getMainHandItem()) && maid.getMainHandItem().getUseDuration(maid) > 0; }
    public static boolean elytra(EntityMaid maid) {
        var chest = maid.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(Items.ELYTRA) && chest.canElytraFly(maid);
    }
    public static boolean fireworks(EntityMaid maid) {
        var inventory = maid.getAvailableInv(false);
        for (int i = 0; i < inventory.getSlots(); i++) if (inventory.getStackInSlot(i).is(Items.FIREWORK_ROCKET)) return true;
        return false;
    }
    public static boolean ready(EntityMaid maid) { return spear(maid) && elytra(maid) && fireworks(maid); }
}
