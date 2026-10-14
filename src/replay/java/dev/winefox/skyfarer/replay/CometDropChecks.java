package dev.winefox.skyfarer.replay;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.item.CometProgress;
import dev.winefox.skyfarer.runtime.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.tags.DamageTypeTags;

/** Production policies, actual ritual/evolution code; world/entity adapters are offline fixtures. */
public final class CometDropChecks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(var type:new Object[]{WineFoxSkyfarer.COMET_HEART.get(),WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()}){
            var world=new ServerLevel();var stack=new ItemStack(type);CometProgress.increment(stack);
            var item=new ItemEntity(world,stack);var identity=item.getUUID();
            for(int tick:new int[]{0,1,120,6000,24000}){
                world.time=tick;CometRelicDrops.refresh(item);
                for(var damage:new Object[]{DamageTypes.LIGHTNING_BOLT,DamageTypeTags.IS_FIRE,DamageTypeTags.IS_EXPLOSION})
                    check(CometRelicDrops.protectedFrom(item,new DamageSource(null,damage)),"relic protection expired");
                check(!CometRelicDrops.protectedFrom(item,new DamageSource(null,new Object())),"blanket invulnerability");
                check(item.glow&&item.getItem()==stack&&item.getUUID().equals(identity)&&CometProgress.read(stack)==1,"glow mutated entity/stack");
            }
            var reloaded=new ItemEntity(world,stack.copy());
            reloaded.getPersistentData().putBoolean("winefox_skyfarer:relic_glow",true);
            CometRelicDrops.refresh(reloaded);check(reloaded.glow,"reload failed to restore glow");
            item.setItem(new ItemStack(Items.TNT));CometRelicDrops.refresh(item);
            check(!item.glow&&!CometRelicDrops.protectedFrom(item,new DamageSource(null,DamageTypeTags.IS_EXPLOSION)),"replacement TNT inherited relic effects");
            var droppedAgain=new ItemEntity(world,stack);CometRelicDrops.refresh(droppedAgain);
            check(droppedAgain.glow&&CometProgress.read(droppedAgain.getItem())==1,"re-drop lost data/effects");
            var priorGlow=new ItemEntity(world,stack);priorGlow.glow=true;CometRelicDrops.refresh(priorGlow);
            priorGlow.setItem(new ItemStack(Items.TNT));CometRelicDrops.refresh(priorGlow);check(priorGlow.glow,"cleared unrelated preexisting glow");
            world.isClientSide=true;var client=new ItemEntity(world,stack);CometRelicDrops.refresh(client);check(!client.glow,"client wrote authoritative flag");
        }
        check(CometRelicDrops.color(new ItemStack(WineFoxSkyfarer.COMET_HEART.get()))==0xFFBE45,"ordinary color");
        check(CometRelicDrops.color(new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()))==0xFF303C,"awakened color");
        check(CometRelicDrops.color(new ItemStack(Items.TNT))==-1&&CometRelicDrops.color(ItemStack.EMPTY)==-1,"unrelated item outlined");
        CometRitualChecks.main(args);
        PropulsionAuthorityChecks.effects();
        System.out.println("Permanent relic damage policy, server glow/reload/re-drop, preserved stack/progress, unrelated items and existing glow passed");
    }
}
