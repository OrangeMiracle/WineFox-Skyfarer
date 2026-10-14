package net.minecraft.world.damagesource;
public record DamageSource(net.minecraft.world.entity.Entity getEntity,Object kind) {
 public DamageSource(net.minecraft.world.entity.Entity entity){this(entity,null);}
 public boolean is(Object type){return kind==type;}
}
