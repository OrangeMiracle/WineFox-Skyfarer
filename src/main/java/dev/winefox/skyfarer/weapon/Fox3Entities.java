package dev.winefox.skyfarer.weapon;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Fox3Entities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, WineFoxSkyfarer.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<Fox3MissileEntity>> MISSILE = TYPES.register("fox3_missile",
        () -> EntityType.Builder.<Fox3MissileEntity>of(Fox3MissileEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F).fireImmune().noSave().noSummon()
            .clientTrackingRange(8).updateInterval(1).setShouldReceiveVelocityUpdates(true)
            .build(WineFoxSkyfarer.MOD_ID + ":fox3_missile"));
    private Fox3Entities() {}
}
