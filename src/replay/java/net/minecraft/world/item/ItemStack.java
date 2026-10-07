package net.minecraft.world.item;
public class ItemStack {
 public static final ItemStack EMPTY=new ItemStack(0,0,false);
 public Object type=Items.FIREWORK_ROCKET;public float strength;public int damage,maxDamage=250;
 public boolean unbreakable;public boolean has(Object component){return component==net.minecraft.core.component.DataComponents.UNBREAKABLE&&unbreakable;}
 public Object getItem(){return type;}public boolean isDamageableItem(){return type!=Items.STONE;}public void setDamageValue(int d){damage=d;}
 public boolean canElytraFly(Object maid){return type==Items.ELYTRA&&damage<maxDamage-1;}
 public ItemStack(Object item){this(1,1,false);type=item;}
 public int getDamageValue(){return damage;}public int getMaxDamage(){return maxDamage;}public int getCount(){return count;}
 public void hurtAndBreak(int n,Object entity,Object slot){damage+=n;}
 public void set(Object key,net.minecraft.world.item.component.Fireworks value){duration=value.flightDuration();}
 public int count; public int duration; public boolean spear;
 public ItemStack(int count,int duration,boolean spear){this.count=count;this.duration=duration;this.spear=spear;}
 public boolean is(Object item){return !spear&&count>0&&type==item;} public boolean isEmpty(){return count<=0;}
 public ItemStack copy(){return new ItemStack(count,duration,spear);}
 public Fireworks get(Object key){return new Fireworks(duration);}
 public record Fireworks(int flightDuration){public java.util.List<Object> explosions(){return java.util.List.of();}}
}
