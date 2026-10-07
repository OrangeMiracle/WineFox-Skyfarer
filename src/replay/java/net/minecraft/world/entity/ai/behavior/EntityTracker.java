package net.minecraft.world.entity.ai.behavior;
import net.minecraft.world.entity.LivingEntity;
public record EntityTracker(LivingEntity entity,boolean eye) implements PositionTracker {
 public LivingEntity getEntity(){return entity;}public net.minecraft.world.phys.Vec3 currentPosition(){return entity.position();}
}
