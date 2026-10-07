package dev.winefox.skyfarer.compat.tlm;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Registry-only compatibility; neither optional mod classes nor fabricated flight abilities. */
public final class WingInventory {
    public static final int MIN_DURABILITY=20;
    private WingInventory() {}
    public enum Qualification { READY, EMPTY_CHEST, NOT_ELYTRA_EQUIVALENT, LOW_DURABILITY, ITEM_CANNOT_GLIDE }
    public static Qualification qualification(ItemStack stack,List<? extends String> extra,Predicate<ItemStack> canFly) {
        if(stack.isEmpty())return Qualification.EMPTY_CHEST;
        if(!recognized(stack,extra))return Qualification.NOT_ELYTRA_EQUIVALENT;
        if(remaining(stack)<MIN_DURABILITY)return Qualification.LOW_DURABILITY;
        return canFly.test(stack)?Qualification.READY:Qualification.ITEM_CANNOT_GLIDE;
    }
    public static boolean recognized(ItemStack stack,List<? extends String> extra) {
        return !stack.isEmpty()&&(stack.is(Items.ELYTRA)||extra.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }
    public static int remaining(ItemStack stack) {
        if(stack.isEmpty())return 0;
        // Components are authoritative even if an optional item/mixin reports damageability differently.
        if(stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)||!stack.isDamageableItem()||stack.getMaxDamage()<=0)
            return Integer.MAX_VALUE;
        return (int)Math.clamp((long)stack.getMaxDamage()-Math.max(0,stack.getDamageValue()),0,Integer.MAX_VALUE);
    }
    public static boolean ready(ItemStack stack,List<? extends String> extra,Predicate<ItemStack> canFly) {
        return qualification(stack,extra,canFly)==Qualification.READY;
    }
    public static ItemStack exchange(IItemHandlerModifiable inventory,ItemStack old,List<? extends String> extra,Predicate<ItemStack> canFly) {
        return exchange(inventory,old,extra,canFly,(slot,reason)->{});
    }
    public static ItemStack exchange(IItemHandlerModifiable inventory,ItemStack old,List<? extends String> extra,Predicate<ItemStack> canFly,
        java.util.function.BiConsumer<Integer,String> trace) {
        for(int slot=0;slot<inventory.getSlots();slot++) {
            var spare=inventory.getStackInSlot(slot);
            if(spare.isEmpty())continue;
            var result=qualification(spare,extra,canFly);
            String reason=result!=Qualification.READY?result.name():spare.getCount()!=1?"STACK_COUNT"
                :!old.isEmpty()&&(!inventory.isItemValid(slot,old)||old.getCount()>inventory.getSlotLimit(slot))?"OLD_CHEST_REJECTED"
                :inventory.extractItem(slot,1,true).isEmpty()?"CANNOT_EXTRACT":"ACCEPTED";
            trace.accept(slot,reason);
            if(!reason.equals("ACCEPTED"))continue;
            inventory.setStackInSlot(slot,old);return spare;
        }
        return ItemStack.EMPTY;
    }
}
