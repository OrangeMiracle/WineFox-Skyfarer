package dev.winefox.skyfarer.mixin;
import net.minecraft.world.item.ItemStack;
public interface LivingRiptideAccessor {void wfs$spinTicks(int ticks);void wfs$spinDamage(float damage);void wfs$spinItem(ItemStack item);void wfs$livingFlag(int flag,boolean value);}
