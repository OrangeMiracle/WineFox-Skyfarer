package net.minecraft.world.entity.ai.memory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
public class MemoryModuleType<T> {
 public static final MemoryModuleType<LivingEntity> ATTACK_TARGET=new MemoryModuleType<>();
 public static final MemoryModuleType<WalkTarget> WALK_TARGET=new MemoryModuleType<>();
 public static final MemoryModuleType<EntityTracker> LOOK_TARGET=new MemoryModuleType<>();
 public static final MemoryModuleType<Boolean> ATTACK_COOLING_DOWN=new MemoryModuleType<>();
}
