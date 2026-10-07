package dev.winefox.skyfarer.replay;
import dev.winefox.skyfarer.compat.tlm.WingInventory;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.List;
public class WingInventoryReplayChecks {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static ItemStack wing(int remaining){var s=new ItemStack(Items.ELYTRA);s.setDamageValue(s.getMaxDamage()-remaining);return s;}
 public static void run(){
        var low=wing(19);var boundary=wing(20);var inventory=new ItemStackHandler(3);
        require(!WingInventory.ready(low,List.of(),s->s.canElytraFly(null)),"19 allowed takeoff");
        require(WingInventory.ready(boundary,List.of(),s->s.canElytraFly(null)),"20 should be allowed");
        require(WingInventory.exchange(inventory,low,List.of(),s->s.canElytraFly(null)).isEmpty(),"invented spare");
        inventory.setStackInSlot(0,wing(18));inventory.setStackInSlot(1,boundary);
        var swapped=WingInventory.exchange(inventory,low,List.of(),s->s.canElytraFly(null));
        require(swapped==boundary&&inventory.getStackInSlot(1)==low&&WingInventory.remaining(inventory.getStackInSlot(0))==18,"swap lost an item or chose unsafe spare");
        var armor=new ItemStack(Items.DIAMOND_CHESTPLATE);
        var ids=List.of("minecraft:diamond_chestplate","cataclysm:ignitium_elytra_chestplate","absent:elytra","invalid id");
        require(WingInventory.recognized(armor,ids),"registered configured equivalent not recognized");
        require(!WingInventory.ready(armor,ids,s->s.canElytraFly(null)),"list granted flight to ordinary armor");
        require(WingInventory.ready(armor,ids,s->true),"mod-provided capability rejected");
        require(!WingInventory.recognized(new ItemStack(Items.STONE),ids),"unknown ID resolved to another item");
        var constrained=new ItemStackHandler(1){@Override public boolean isItemValid(int slot,ItemStack s){return s!=low;}};
        constrained.setStackInSlot(0,boundary);require(WingInventory.exchange(constrained,low,List.of(),s->true).isEmpty(),"overwrote inventory rejecting old wing");

 }
}
