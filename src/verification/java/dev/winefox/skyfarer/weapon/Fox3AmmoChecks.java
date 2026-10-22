package dev.winefox.skyfarer.weapon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.*;

/** Real mapped ItemStack/components and NeoForge item-handler transactions; no client/server. */
public final class Fox3AmmoChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var inventory = new ItemStackHandler(3);
        int[] spawned = {0}, rolledBack = {0};
        var noAccess = new ItemStackHandler(0) {
            @Override public int getSlots() { throw new AssertionError("free launch inspected inventory"); }
        };
        require(Fox3Ammo.available(noAccess, false) && Fox3Ammo.launch(noAccess, false, () -> true, () -> {}), "free launch unavailable");
        inventory.setStackInSlot(0, new ItemStack(Items.FIREWORK_STAR, 64));
        require(!Fox3Ammo.launch(inventory, true, () -> { spawned[0]++; return true; }, () -> rolledBack[0]++), "star accepted as rocket");
        require(spawned[0] == 0, "no-ammo still spawned");
        inventory.setStackInSlot(1, new ItemStack(Items.FIREWORK_ROCKET, 3));
        for (int i = 0; i < 8; i++) if (Fox3Ammo.launch(inventory, true, () -> { spawned[0]++; return true; }, () -> rolledBack[0]++)) { }
        require(spawned[0] == 3 && inventory.getStackInSlot(1).isEmpty(), "partial salvo duplicated ammunition");
        var decorated = new ItemStack(Items.FIREWORK_ROCKET, 2);
        decorated.set(DataComponents.FIREWORKS, new Fireworks(3, List.of(new FireworkExplosion(FireworkExplosion.Shape.STAR,
            it.unimi.dsi.fastutil.ints.IntList.of(0x123456), it.unimi.dsi.fastutil.ints.IntList.of(), true, true))));
        inventory.setStackInSlot(2, decorated);
        require(Fox3Ammo.launch(inventory, true, () -> true, () -> rolledBack[0]++), "decorated firework rejected");
        require(inventory.getStackInSlot(2).getCount() == 1 && inventory.getStackInSlot(2).get(DataComponents.FIREWORKS).equals(decorated.get(DataComponents.FIREWORKS)), "ammunition components corrupted");
        require(!Fox3Ammo.launch(inventory, true, () -> false, () -> rolledBack[0]++), "cancelled spawn accepted");
        require(inventory.getStackInSlot(2).getCount() == 1, "cancelled spawn charged ammunition");
        require(!Fox3Ammo.launch(inventory, true, () -> { inventory.setStackInSlot(2, ItemStack.EMPTY); return true; }, () -> rolledBack[0]++), "spawn callback inventory race accepted");
        require(rolledBack[0] == 1, "unpaid rocket not discarded");
        var refusing = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return simulate ? super.extractItem(slot, amount, true) : ItemStack.EMPTY; }
        };
        refusing.setStackInSlot(0, new ItemStack(Items.FIREWORK_ROCKET));
        require(!Fox3Ammo.launch(refusing, true, () -> true, () -> rolledBack[0]++), "failed real extraction accepted");
        require(rolledBack[0] == 2 && refusing.getStackInSlot(0).getCount() == 1, "extraction failure duplicated item");
        System.out.println("Fox3 ammunition: free mode performs zero inventory reads; ordinary/decorated rockets, 3-of-8 stock, cancellation, spawn callback mutation and refused extraction passed");
    }
}
