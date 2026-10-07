package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Player.startAutoSpinAttack's LivingEntity state, without constructing a fake player. */
@Mixin(LivingEntity.class)
public interface LivingRiptideAccessor {
    @Accessor("autoSpinAttackTicks") void wfs$spinTicks(int ticks);
    @Accessor("autoSpinAttackDmg") void wfs$spinDamage(float damage);
    @Accessor("autoSpinAttackItemStack") void wfs$spinItem(ItemStack item);
    @Invoker("setLivingEntityFlag") void wfs$livingFlag(int flag,boolean value);
}
