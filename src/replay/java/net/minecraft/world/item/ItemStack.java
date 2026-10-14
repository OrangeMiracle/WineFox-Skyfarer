package net.minecraft.world.item;
public class ItemStack {
 public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return !a.isEmpty()&&!b.isEmpty()&&a.type==b.type&&a.duration==b.duration;}
 public net.minecraft.world.item.component.CustomData custom=net.minecraft.world.item.component.CustomData.EMPTY;
 public net.minecraft.world.item.component.CustomData getOrDefault(Object key,net.minecraft.world.item.component.CustomData fallback){return custom;}
 public Object getComponentsPatch(){return custom;}
 public ItemStack(Object item,int count,Object patch){this(item);this.count=count;custom=(net.minecraft.world.item.component.CustomData)patch;}
 public void shrink(int n){count-=n;}
 public static final ItemStack EMPTY=new ItemStack(0,0,false);
 public Object type=Items.FIREWORK_ROCKET;public float strength;public int damage,maxDamage=250;
 public boolean unbreakable;public boolean has(Object component){return component==net.minecraft.core.component.DataComponents.UNBREAKABLE&&unbreakable;}
 public Object getItem(){return type;}public boolean isDamageableItem(){return type!=Items.STONE;}public void setDamageValue(int d){damage=d;}
 public int getUseDuration(Object user){return 72000;}public Enchantments getEnchantments(){return new Enchantments(Math.max(0,(int)(strength/0.75f-1)));}
 public record Enchantments(int level){public java.util.Set<Entry> entrySet(){return java.util.Set.of(new Entry(level));}}
 public record Entry(int level){public Key getKey(){return new Key();}public int getIntValue(){return level;}}
 public record Key(){public boolean is(Object key){return key==net.minecraft.world.item.enchantment.Enchantments.RIPTIDE;}}
 public boolean canElytraFly(Object maid){return type==Items.ELYTRA&&damage<maxDamage-1;}
 public ItemStack(Object item){this(1,1,false);type=item;}
 public void releaseUsing(Object world,com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid m,int remaining){m.releaseCallbacks++;dev.winefox.skyfarer.compat.tlm.RiptidePropulsion.releaseAuthorized(m,this,remaining);}
 public int getDamageValue(){return damage;}public int getMaxDamage(){return maxDamage;}public int getCount(){return count;}
 public void hurtAndBreak(int n,Object entity,Object slot){damage+=n;}
 public void set(Object key,net.minecraft.world.item.component.Fireworks value){duration=value.flightDuration();}
 public int count; public int duration; public boolean spear;
 public ItemStack(int count,int duration,boolean spear){this.count=count;this.duration=duration;this.spear=spear;}
 public boolean is(Object item){return !spear&&count>0&&type==item;} public boolean isEmpty(){return count<=0;}
 public void setCount(int count){this.count=count;}
 public ItemStack copy(){var copy=new ItemStack(count,duration,spear);copy.custom=custom;copy.type=type;copy.strength=strength;copy.damage=damage;copy.maxDamage=maxDamage;copy.unbreakable=unbreakable;return copy;}
 public Fireworks get(Object key){return new Fireworks(duration);}
 public record Fireworks(int flightDuration){public java.util.List<Object> explosions(){return java.util.List.of();}}
}
