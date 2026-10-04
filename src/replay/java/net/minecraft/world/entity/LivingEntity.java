package net.minecraft.world.entity;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.ItemStack;
import dev.winefox.skyfarer.replay.ReplayWorld;
public class LivingEntity {
 public ReplayWorld world; public Vec3 pos=Vec3.ZERO,velocity=Vec3.ZERO; public float yaw,pitch,yBodyRot; public boolean alive=true,ground=true,hostile=true;
 public int usedTicks; public boolean using; public float width=.6f,height=1.8f; public java.util.UUID id=java.util.UUID.randomUUID();
 public final ItemStack weapon=new ItemStack(1,0,true);
 public LivingEntity(ReplayWorld w,Vec3 pos){world=w;this.pos=pos;w.entities.add(this);}
 public Vec3 position(){return pos;} public double getX(){return pos.x;} public double getY(){return pos.y;} public double getZ(){return pos.z;}
 public double getEyeY(){return pos.y+getEyeHeight();} public float getEyeHeight(){return 1.6f;} public Vec3 getEyePosition(){return pos.add(0,getEyeHeight(),0);}
 public Vec3 getDeltaMovement(){return velocity;} public void setDeltaMovement(Vec3 v){velocity=v;}
 public float getXRot(){return pitch;} public float getYRot(){return yaw;} public void setXRot(float v){pitch=v;} public void setYRot(float v){yaw=v;}
 public void setYHeadRot(float v){} public void setYBodyRot(float v){yBodyRot=v;}
 public Vec3 getLookAngle(){double p=Math.toRadians(pitch),y=Math.toRadians(yaw);return new Vec3(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));}
 public ReplayWorld level(){return world;} public boolean isAlive(){return alive;} public boolean isRemoved(){return !alive;}
 public boolean isSpectator(){return false;} public boolean isInvulnerable(){return false;} public boolean onGround(){return ground;}
 public double distanceTo(LivingEntity e){return pos.distanceTo(e.pos);} public java.util.UUID getUUID(){return id;} public int getId(){return id.hashCode();}
 public float getBbWidth(){return width;} public float getBbHeight(){return height;}
 public AABB getBoundingBox(){return new AABB(pos.x-width/2,pos.y,pos.z-width/2,pos.x+width/2,pos.y+getBbHeight(),pos.z+width/2);}
 public Name getName(){return new Name(hostile?"enemy":"maid");} public record Name(String text){public String getString(){return text;}}
 public boolean isUsingItem(){return using;} public int getTicksUsingItem(){return usedTicks;} public ItemStack getMainHandItem(){return weapon;} public ItemStack getUseItem(){return weapon;}
}
