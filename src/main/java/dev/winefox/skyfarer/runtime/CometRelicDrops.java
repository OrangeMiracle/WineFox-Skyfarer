package dev.winefox.skyfarer.runtime;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Shared dropped-relic policy. Never changes an ItemStack, pickup delay, age or owner. */
public final class CometRelicDrops {
    private static final String OWNED_GLOW="winefox_skyfarer:relic_glow";
    private static final String PREVIOUS_GLOW="winefox_skyfarer:previous_glow";
    private CometRelicDrops() {}

    public static int color(ItemStack stack){
        if(stack.isEmpty())return -1;
        if(stack.is(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()))return 0xFF303C;
        return stack.is(WineFoxSkyfarer.COMET_HEART.get())?0xFFBE45:-1;
    }
    public static boolean protectedDamage(DamageSource source){
        return source.is(DamageTypes.LIGHTNING_BOLT)||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION);
    }
    public static boolean protectedFrom(ItemEntity item,DamageSource source){
        return (color(item.getItem())!=-1||CometRitual.protectedItem(item))&&protectedDamage(source);
    }
    public static void refresh(ItemEntity item){
        if(item.level().isClientSide)return;
        var data=item.getPersistentData();
        if(color(item.getItem())!=-1){
            if(!data.getBoolean(OWNED_GLOW)){
                data.putBoolean(PREVIOUS_GLOW,item.isCurrentlyGlowing());
                data.putBoolean(OWNED_GLOW,true);
            }
            if(!item.isCurrentlyGlowing())item.setGlowingTag(true);
        }else if(data.getBoolean(OWNED_GLOW)){
            // A mod may replace the stack of an existing entity. Restore its previous flag.
            item.setGlowingTag(data.getBoolean(PREVIOUS_GLOW));
            data.remove(OWNED_GLOW);data.remove(PREVIOUS_GLOW);
        }
    }
}
