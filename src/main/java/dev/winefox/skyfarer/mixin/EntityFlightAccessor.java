package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Non-player LivingEntity already has Elytra physics; only the protected flag setter is missing. */
@Mixin(Entity.class)
public interface EntityFlightAccessor {
    @Invoker("setSharedFlag") void wfs$setSharedFlag(int flag, boolean value);
}
