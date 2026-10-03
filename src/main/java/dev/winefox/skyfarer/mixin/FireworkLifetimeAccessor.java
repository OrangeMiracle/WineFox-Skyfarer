package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkLifetimeAccessor {
    @Accessor("life") int wfs$life();
    @Accessor("lifetime") int wfs$lifetime();
}
