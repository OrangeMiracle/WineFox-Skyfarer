package com.github.tartaricacid.touhoulittlemaid.entity.passive;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import dev.winefox.skyfarer.replay.ReplayWorld;
public class EntityMaid extends LivingEntity implements dev.winefox.skyfarer.mixin.LivingRiptideAccessor {
 public net.minecraft.nbt.CompoundTag persistent=new net.minecraft.nbt.CompoundTag();public net.minecraft.nbt.CompoundTag getPersistentData(){return persistent;}
 public java.util.UUID getOwnerUUID(){return owner==null?null:owner.getUUID();}
 public ItemStack getItemBySlot(Object slot){return new ItemStack(net.minecraft.world.item.Items.ELYTRA);}
 public final Brain brain=new Brain();public final Navigation navigation=new Navigation();public int meleeHits,swings;
 public double interactionRange=2;public int favorabilityBonus;
 public Brain getBrain(){return brain;}public Navigation getNavigation(){return navigation;}
 public void lookAt(LivingEntity target,float yawRate,float pitchRate){var d=target.getEyePosition().subtract(getEyePosition());yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));pitch=(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()));}
 public float getYHeadRot(){return yaw;}public void swing(Object hand){swings++;}
 public boolean hasLineOfSight(LivingEntity target){return world.ray(getEyePosition(),target.getEyePosition());}
 public boolean doHurtTarget(LivingEntity target){if(dev.winefox.skyfarer.compat.spears.SpearCompat.groundContact(this,target)){meleeHits++;return true;}return false;}
 public static class Navigation {public int stops;public void stop(){stops++;}}
 public static class Brain {
  final java.util.Map<Object,Object> memory=new java.util.HashMap<>();
  @SuppressWarnings("unchecked") public <T> java.util.Optional<T> getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType<T> key){return java.util.Optional.ofNullable((T)memory.get(key));}
  public <T> void setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType<T> key,T value){memory.put(key,value);}
  public <T> void setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType<T> key,T value,long ticks){setMemory(key,value);}
  public void eraseMemory(Object key){memory.remove(key);}public boolean hasMemoryValue(Object key){return memory.containsKey(key);}
 }
 public int wingDurability=432,spareWings,groundAttacks,spinTicks;public boolean rain,water;public java.util.List<String> reports=new java.util.ArrayList<>();
 public java.util.List<Long> reportTimes=new java.util.ArrayList<>();
 public boolean isAutoSpinAttack(){return spinTicks>0;}
 public ItemStack offhand=ItemStack.EMPTY;public final Backpack backpack=new Backpack();
 public boolean isInWaterOrRain(){return water||rain;}public ItemStack getOffhandItem(){return offhand;}
 public Backpack getAvailableBackpackInv(){return backpack;}
 public void setItemSlot(Object slot,ItemStack stack){offhand=stack;}
 public void startUsingItem(Object hand){using=true;usedTicks=0;}public void stopUsingItem(){using=false;}
 @Override public ItemStack getUseItem(){return using&&offhand!=ItemStack.EMPTY?offhand:weapon;}
 public void push(double x,double y,double z){velocity=velocity.add(x,y,z);}public Object getSoundSource(){return this;}
 public void wfs$spinTicks(int ticks){spinTicks=ticks;}public void wfs$spinDamage(float damage){}public void wfs$spinItem(ItemStack item){}public void wfs$livingFlag(int flag,boolean value){}
 public static class Backpack {public ItemStack stack=ItemStack.EMPTY;public int getSlots(){return 1;}public ItemStack getStackInSlot(int i){return stack;}public ItemStack extractItem(int i,int n,boolean sim){return stack;}public void setStackInSlot(int i,ItemStack s){stack=s;}public boolean isItemValid(int i,ItemStack s){return true;}public int getSlotLimit(int i){return 64;}}
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
