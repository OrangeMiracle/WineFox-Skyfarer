package dev.winefox.skyfarer.runtime;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.compat.tlm.CometBaubles;
import dev.winefox.skyfarer.mixin.ItemThrowerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import java.util.*;

/** Dropped-item ritual, independent of maids/equipment/weather. One transaction per actual bolt. */
public final class CometRitual {
    private static final String PROCESSED="winefox_skyfarer:comet_ritual";
    private static final String PROTECTED_UNTIL="winefox_skyfarer:comet_ward_until";
    private static final int WARD_TICKS=120;
    private record Payment(ItemEntity entity,ItemStack expected,ItemStack remainder) {}
    private CometRitual() {}

    public static boolean strike(LightningBolt bolt,boolean visualOnly,int life){
        if(visualOnly||life!=2||bolt.isRemoved()||!(bolt.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
            ||bolt.getPersistentData().getBoolean(PROCESSED))return false;
        bolt.getPersistentData().putBoolean(PROCESSED,true);
        var local=level.getEntitiesOfClass(ItemEntity.class,bolt.getBoundingBox().inflate(3),item->
            item.isAlive()&&!item.isRemoved()&&item.position().subtract(bolt.position()).lengthSqr()<=9);
        var heart=local.stream().filter(item->CometBaubles.ordinary(item.getItem()))
            .min(Comparator.comparingInt(Entity::getId)).orElse(null);
        if(heart==null)return false;
        var fuel=local.stream().filter(item->item.getItem().is(Items.TNT)).sorted(Comparator.comparingInt(Entity::getId)).toList();
        int remaining=64;var payments=new ArrayList<Payment>();
        for(var item:fuel){
            if(remaining==0)break;
            var expected=item.getItem();int amount=Math.min(remaining,expected.getCount());
            var rest=expected.copy();rest.shrink(amount);payments.add(new Payment(item,expected,rest));remaining-=amount;
        }
        if(remaining!=0)return false;
        var expectedHeart=heart.getItem();
        var result=new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get().builtInRegistryHolder(),1,expectedHeart.getComponentsPatch());
        UUID owner=rewardOwner(level,bolt,heart);
        var rewards=CometRewards.get(level.getServer());
        // Prepare before payment. Retain the ItemEntity UUID/thrower/pickup contract: no spawn
        // event can veto a result after materials have been committed, and no second entity exists.
        if(!heart.isAlive()||heart.isRemoved()||heart.getItem()!=expectedHeart||!CometBaubles.ordinary(expectedHeart))return false;
        for(var p:payments)if(!p.entity.isAlive()||p.entity.isRemoved()||p.entity.getItem()!=p.expected||p.entity.level()!=level)return false;
        for(var p:payments)p.entity.setItem(p.remainder);
        heart.setItem(result);ward(heart);CometRelicDrops.refresh(heart);
        for(var item:local)if(CometBaubles.ordinary(item.getItem()))ward(item);
        // Preserve surplus too: vanilla's following thunderHit must not destroy the remainder.
        for(var item:fuel)if(!item.getItem().isEmpty())ward(item);
        for(var p:payments)if(p.remainder.isEmpty())p.entity.discard();
        rewards.earned(level.getServer(),owner);
        return true;
    }
    private static UUID rewardOwner(ServerLevel level,LightningBolt bolt,ItemEntity heart){
        if(bolt.getCause()!=null)return bolt.getCause().getUUID();
        UUID thrower=((ItemThrowerAccessor)heart).wfs$thrower();
        var profiles=level.getServer().getProfileCache();
        // A thrower UUID can name a mob. A server-cached profile supplies positive player
        // evidence, including an offline thrower. Unknown attribution rewards no bystander.
        return thrower!=null&&profiles!=null&&profiles.get(thrower).isPresent()?thrower:null;
    }
    private static void ward(ItemEntity item){item.getPersistentData().putLong(PROTECTED_UNTIL,item.level().getGameTime()+WARD_TICKS);}
    public static boolean protectedItem(ItemEntity item){
        long until=item.getPersistentData().getLong(PROTECTED_UNTIL),now=item.level().getGameTime();
        return until>0&&now>=until-WARD_TICKS&&now<until;
    }
    public static boolean protectedFrom(ItemEntity item,DamageSource source){
        return protectedItem(item)&&CometRelicDrops.protectedDamage(source);
    }
}
