package net.minecraft.world.entity.ai.memory;
public record WalkTarget(Object target,float speed,int closeEnough) {
 public net.minecraft.world.entity.ai.behavior.PositionTracker getTarget(){
  return target instanceof net.minecraft.world.entity.ai.behavior.PositionTracker p?p:()->(net.minecraft.world.phys.Vec3)target;
 }
}
