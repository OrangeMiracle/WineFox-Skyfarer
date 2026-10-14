package net.minecraft.world.entity;
public class LightningBolt extends LivingEntity {
 public net.minecraft.server.level.ServerPlayer cause;
 public net.minecraft.server.level.ServerPlayer getCause(){return cause;}
 public LightningBolt(dev.winefox.skyfarer.replay.ReplayWorld w){super(w,net.minecraft.world.phys.Vec3.ZERO);hostile=false;}
}
