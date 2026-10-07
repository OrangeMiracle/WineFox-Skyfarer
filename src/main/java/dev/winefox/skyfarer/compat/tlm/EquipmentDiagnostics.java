package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.WeakHashMap;

/** Main-thread diagnostics independent of persistent dialogue deduplication. */
public final class EquipmentDiagnostics {
    private record Last(String fingerprint,long time) {}
    private static final WeakHashMap<EntityMaid,Last> LAST=new WeakHashMap<>();
    private EquipmentDiagnostics() {}
    public static void clear(){LAST.clear();}
    private static String describe(EntityMaid maid,ItemStack stack) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return "item="+id+" listMatch="+SkyfarerSettings.extraElytraItems().contains(id)
            +" equivalent="+SkyfarerEquipment.wing(stack)+" damage="+stack.getDamageValue()+" maxDamage="+stack.getMaxDamage()
            +" damageable="+stack.isDamageableItem()+" unbreakable="+stack.has(DataComponents.UNBREAKABLE)
            +" maxDamageComponent="+stack.has(DataComponents.MAX_DAMAGE)+" damageComponent="+stack.has(DataComponents.DAMAGE)
            +" remaining="+WingInventory.remaining(stack)+" canFly="+(!stack.isEmpty()&&stack.canElytraFly(maid));
    }
    public static boolean begin(EntityMaid maid,ItemStack chest,WingInventory.Qualification result) {
        if(!SkyfarerSettings.debugLogging()){LAST.remove(maid);return false;}
        String fingerprint=describe(maid,chest)+" result="+result+" list="+SkyfarerSettings.extraElytraItems();
        long now=maid.level().getGameTime();var old=LAST.get(maid);
        if(old!=null&&old.fingerprint.equals(fingerprint)&&now>=old.time&&now-old.time<200)return false;
        LAST.put(maid,new Last(fingerprint,now));
        WineFoxSkyfarer.LOGGER.info("[WFS debug] EQUIPMENT_CHECK maid={} {}",maid.getUUID(),fingerprint);
        return true;
    }
    public static void spare(EntityMaid maid,int slot,ItemStack stack,String reason) {
        WineFoxSkyfarer.LOGGER.info("[WFS debug] EQUIPMENT_SPARE maid={} slot={} {} result={}",maid.getUUID(),slot,describe(maid,stack),reason);
    }
    public static void result(EntityMaid maid,WingInventory.Qualification before,String replacement,WingInventory.Qualification after) {
        WineFoxSkyfarer.LOGGER.info("[WFS debug] EQUIPMENT_RESULT maid={} before={} replacement={} equipmentQualified={} reason={} spear={} fireworks={} ground={} glide={}",
            maid.getUUID(),before,replacement,after==WingInventory.Qualification.READY,after,SkyfarerEquipment.spear(maid),SkyfarerEquipment.fireworks(maid),maid.onGround(),maid.isFallFlying());
    }
}
