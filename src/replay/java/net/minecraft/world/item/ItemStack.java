package net.minecraft.world.item;
public class ItemStack {
 public ItemStack(Object item){this(1,1,false);}
 public void set(Object key,net.minecraft.world.item.component.Fireworks value){duration=value.flightDuration();}
 public int count; public int duration; public boolean spear;
 public ItemStack(int count,int duration,boolean spear){this.count=count;this.duration=duration;this.spear=spear;}
 public boolean is(Object item){return !spear&&count>0;} public boolean isEmpty(){return count<=0;}
 public ItemStack copy(){return new ItemStack(count,duration,spear);}
 public Fireworks get(Object key){return new Fireworks(duration);}
 public record Fireworks(int flightDuration){public java.util.List<Object> explosions(){return java.util.List.of();}}
}
