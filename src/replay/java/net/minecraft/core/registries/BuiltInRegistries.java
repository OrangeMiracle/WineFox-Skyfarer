package net.minecraft.core.registries;
import net.minecraft.world.item.Items;
public class BuiltInRegistries {public static final Registry ITEM=new Registry();public static class Registry {public String getKey(Object item){return item instanceof String id?id:item==Items.TRIDENT?"minecraft:trident":item==Items.ELYTRA?"minecraft:elytra":item==Items.DIAMOND_CHESTPLATE?"minecraft:diamond_chestplate":"minecraft:stone";}}}
