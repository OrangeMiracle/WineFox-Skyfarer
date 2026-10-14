package dev.winefox.skyfarer.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.UUID;

/** Vanilla persists Thrower; getOwner() only resolves a currently loaded entity. */
@Mixin(ItemEntity.class)
public interface ItemThrowerAccessor {
    @Accessor("thrower") UUID wfs$thrower();
}
