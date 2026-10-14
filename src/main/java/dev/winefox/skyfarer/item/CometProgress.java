package dev.winefox.skyfarer.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stack-owned, namespaced and serialized by vanilla CUSTOM_DATA. No maid UUID binding. */
public final class CometProgress {
    public static final int REQUIRED=100;
    private static final String KEY="winefox_skyfarer:comet_kills";
    private CometProgress() {}
    public static int read(ItemStack stack){return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt(KEY),0,REQUIRED);}
    public static int increment(ItemStack stack){int next=Math.min(REQUIRED,read(stack)+1);CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putInt(KEY,next));return next;}
}
