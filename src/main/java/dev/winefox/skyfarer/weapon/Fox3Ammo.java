package dev.winefox.skyfarer.weapon;

import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

/** Weapon ammunition only: no propulsion resolver, Comet saving roll or creative-transit exemption. */
public final class Fox3Ammo {
    private Fox3Ammo() {}
    private static int slot(IItemHandler inventory) {
        for (int i = 0; i < inventory.getSlots(); i++)
            if (inventory.getStackInSlot(i).is(Items.FIREWORK_ROCKET) && inventory.extractItem(i, 1, true).is(Items.FIREWORK_ROCKET)) return i;
        return -1;
    }
    public static boolean available(IItemHandler inventory, boolean consume) { return !consume || slot(inventory) >= 0; }
    public static boolean launch(IItemHandler inventory, boolean consume, BooleanSupplier spawn, Runnable rollback) {
        if (!consume) return spawn.getAsBoolean();
        int selected = slot(inventory);
        if (selected < 0) return false;
        ItemStack expected = inventory.extractItem(selected, 1, true).copy();
        if (!spawn.getAsBoolean()) return false;
        // EntityJoinLevelEvent may run arbitrary mod callbacks: recheck before charging ammunition.
        ItemStack current = inventory.extractItem(selected, 1, true);
        if (!ItemStack.isSameItemSameComponents(expected, current) || current.getCount() != 1) {
            rollback.run(); return false;
        }
        ItemStack consumed = inventory.extractItem(selected, 1, false);
        if (consumed.getCount() != 1 || !ItemStack.isSameItemSameComponents(expected, consumed)) {
            rollback.run(); return false;
        }
        return true;
    }
}
