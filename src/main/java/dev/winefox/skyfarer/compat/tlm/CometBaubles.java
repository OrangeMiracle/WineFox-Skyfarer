package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.world.item.ItemStack;

/** Main-thread native equipment lookup. Backpacks, hands and player equipment grant no effect. */
public final class CometBaubles {
    private CometBaubles() {}
    public static void bind(BaubleManager manager) {
        manager.bind(WineFoxSkyfarer.COMET_HEART.get(),new IMaidBauble() {});
        manager.bind(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get(),new IMaidBauble() {});
    }
    public static int ordinarySlot(EntityMaid maid) {
        var inventory=maid.getMaidBauble();int found=-1;
        for(int slot=0;slot<inventory.getSlots();slot++) {
            var stack=inventory.getStackInSlot(slot);
            if(stack.is(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()))return -1;
            if(found<0&&ordinary(stack))found=slot;
        }
        return found;
    }
    public static boolean ordinary(ItemStack stack){return stack.getCount()==1&&stack.is(WineFoxSkyfarer.COMET_HEART.get());}
    public static boolean virtual(EntityMaid maid) {
        if(!SkyfarerRuntime.isSkyfarer(maid))return false;
        var inventory=maid.getMaidBauble();
        for(int slot=0;slot<inventory.getSlots();slot++)if(inventory.getStackInSlot(slot).is(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()))return true;
        return false;
    }
    public static boolean saving(EntityMaid maid){return SkyfarerRuntime.isSkyfarer(maid)&&ordinarySlot(maid)>=0;}
}
