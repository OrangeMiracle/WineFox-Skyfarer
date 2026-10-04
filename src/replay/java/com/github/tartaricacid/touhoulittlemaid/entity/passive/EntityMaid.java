package com.github.tartaricacid.touhoulittlemaid.entity.passive;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import dev.winefox.skyfarer.replay.ReplayWorld;
public class EntityMaid extends LivingEntity {
 public LivingEntity owner; public boolean glide,home,task=true,equipped=true,guiOpening; public int teleports; public Pose pose=Pose.STANDING;
 public final Inventory inventory=new Inventory(); public final Swim swim=new Swim(); public BlockPos homePos=new BlockPos(0,0,0);
 public EntityMaid(ReplayWorld w,Vec3 p){super(w,p);hostile=false;height=1.5f;}
 public LivingEntity getOwner(){return owner;} public boolean isFallFlying(){return glide;} public void setPose(Pose p){pose=p;}
 public BlockPos blockPosition(){return BlockPos.containing(pos);}
 @Override public float getEyeHeight(){return pose==Pose.FALL_FLYING?.4f:1.3f;} @Override public float getBbHeight(){return pose==Pose.FALL_FLYING?.6f:1.5f;}
 public boolean isNoAi(){return false;} public boolean isMaidInSittingPose(){return false;} public boolean isSleeping(){return false;}
 public boolean isPassenger(){return false;} public boolean isLeashed(){return false;} public boolean isInWaterOrBubble(){return false;}
 public boolean isAlliedTo(LivingEntity t){return !t.hostile;} public boolean canAttack(LivingEntity t){return t.hostile;}
 public double getAttributeValue(Object attr){return .08;} public boolean isHomeModeEnable(){return home;} public BlockPos getRestrictCenter(){return homePos;} public int getRestrictRadius(){return 8;}
 public Inventory getAvailableInv(boolean ignored){return inventory;} public Swim getSwimManager(){return swim;} public Jump getJumpControl(){return new Jump();}
 public class Jump {public void jump(){if(ground){ground=false;velocity=new Vec3(velocity.x,.42,velocity.z);}}}
 public static class Swim {boolean value;public boolean wantToSwim(){return value;}public void setWantToSwim(boolean v){value=v;}}
 public static class Inventory {
  public int count=64,duration=1,consumed;
  public int getSlots(){return 1;} public ItemStack getStackInSlot(int i){return new ItemStack(count,duration,false);}
  public ItemStack extractItem(int i,int n,boolean simulate){int take=Math.min(n,count);if(!simulate){count-=take;consumed+=take;}return new ItemStack(take,duration,false);}
 }
 public void teleportTo(double x,double y,double z){teleports++;pos=new Vec3(x,y,z);velocity=Vec3.ZERO;ground=true;}
 public void teleportToOwner(LivingEntity target){teleportTo(target.getX(),target.getY(),target.getZ());}
}
