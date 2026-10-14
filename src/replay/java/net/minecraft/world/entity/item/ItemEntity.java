package net.minecraft.world.entity.item;
public class ItemEntity extends net.minecraft.world.entity.LivingEntity implements dev.winefox.skyfarer.mixin.ItemThrowerAccessor {
 public boolean glow;public boolean isCurrentlyGlowing(){return glow;}public void setGlowingTag(boolean value){glow=value;}
 public java.util.UUID thrower;public java.util.UUID wfs$thrower(){return thrower;}
 private net.minecraft.world.item.ItemStack item;
 public ItemEntity(dev.winefox.skyfarer.replay.ReplayWorld w,net.minecraft.world.item.ItemStack stack){super(w,net.minecraft.world.phys.Vec3.ZERO);item=stack;hostile=false;}
 public net.minecraft.world.item.ItemStack getItem(){return item;}
 public void setItem(net.minecraft.world.item.ItemStack value){item=value;}
 public void discard(){alive=false;}
}
