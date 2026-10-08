package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import dev.winefox.skyfarer.config.SkyfarerSettings;

public final class SkyfarerEquipment {
    public static final int MIN_WING_DURABILITY = WingInventory.MIN_DURABILITY;
    private SkyfarerEquipment() {}
    public static void groundCombat(EntityMaid maid) { GroundSpearCombat.tick(maid); }
    public static void report(EntityMaid maid,String event) {
        boolean generic=!event.equals("landed")&&wingQualification(maid)!=WingInventory.Qualification.LOW_DURABILITY;
        maid.getChatBubbleManager().addTextChatBubble("message.winefox_skyfarer.wings_"+event+(generic?"_equipment":""));
    }
    public static void updateNotices(EntityMaid maid,boolean ready,boolean groundMovement) { EquipmentNotices.tick(maid,ready,groundMovement); }
    public static boolean spear(EntityMaid maid) { return SpearCompat.isChargeWeapon(maid.getMainHandItem()) && maid.getMainHandItem().getUseDuration(maid) > 0; }
    public static boolean elytra(EntityMaid maid) {
        var chest = maid.getItemBySlot(EquipmentSlot.CHEST);
        return wing(chest) && chest.canElytraFly(maid);
    }
    public static boolean wing(ItemStack stack) {
        return WingInventory.recognized(stack,SkyfarerSettings.extraElytraItems());
    }
    public static int remaining(ItemStack stack) {
        return WingInventory.remaining(stack);
    }
    public static boolean flightReady(EntityMaid maid) {
        return wingQualification(maid)==WingInventory.Qualification.READY;
    }
    public static WingInventory.Qualification wingQualification(EntityMaid maid) {
        return WingInventory.qualification(maid.getItemBySlot(EquipmentSlot.CHEST),SkyfarerSettings.extraElytraItems(),s->s.canElytraFly(maid));
    }
    /** Main thread only. TLM's CombinedInvWrapper is modifiable; exchange preserves both stacks. */
    public static boolean ensureWings(EntityMaid maid) {
        var chest=maid.getItemBySlot(EquipmentSlot.CHEST);
        var before=wingQualification(maid);
        boolean trace=EquipmentDiagnostics.begin(maid,chest,before);
        if(before==WingInventory.Qualification.READY){if(trace)EquipmentDiagnostics.result(maid,before,"NOT_NEEDED",before);return true;}
        var inventory=maid.getAvailableInv(false);
        var spare=WingInventory.exchange(inventory,chest,SkyfarerSettings.extraElytraItems(),stack->stack.canElytraFly(maid),
            (slot,reason)->{if(trace)EquipmentDiagnostics.spare(maid,slot,inventory.getStackInSlot(slot),reason);});
        if(spare.isEmpty()){if(trace)EquipmentDiagnostics.result(maid,before,"NO_USABLE_SPARE slots="+inventory.getSlots(),before);return false;}
        maid.setItemSlot(EquipmentSlot.CHEST,spare);
        var after=wingQualification(maid);
        if(trace)EquipmentDiagnostics.result(maid,before,"REPLACED",after);
        if(SkyfarerSettings.debugLogging())dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] WINGS_REPLACED maid={} remaining={} airborne={}",maid.getUUID(),remaining(spare),!maid.onGround());
        return after==WingInventory.Qualification.READY;
    }
    public static boolean fireworks(EntityMaid maid) {
        var inventory = maid.getAvailableInv(false);
        for (int i = 0; i < inventory.getSlots(); i++) if (inventory.getStackInSlot(i).is(Items.FIREWORK_ROCKET)) return true;
        return false;
    }
    public static boolean ready(EntityMaid maid) { return spear(maid) && elytra(maid) && PropulsionAvailability.rejection(maid)==null; }
}
