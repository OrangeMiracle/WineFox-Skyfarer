package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.compat.tlm.CometBaubles;
import dev.winefox.skyfarer.item.CometProgress;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.*;

/** Server-thread transactions only. Invoked after cancellation gates, never from prediction. */
public final class CometEvolution {
    private static final String COUNTED="winefox_skyfarer:comet_counted";
    private CometEvolution() {}
    public static void killed(LivingEntity victim,DamageSource source) {
        if(!(victim instanceof Creeper)||!(victim.level() instanceof ServerLevel)||!victim.isDeadOrDying()
            ||victim.getPersistentData().getBoolean(COUNTED)||!(source.getEntity() instanceof EntityMaid maid)||!SkyfarerRuntime.isSkyfarer(maid))return;
        // Mark even an unequipped kill: re-entering die() must never credit another stack.
        victim.getPersistentData().putBoolean(COUNTED,true);
        int slot=CometBaubles.ordinarySlot(maid);if(slot<0)return;
        var stack=maid.getMaidBauble().getStackInSlot(slot);
        if(CometProgress.increment(stack)>=CometProgress.REQUIRED)evolve(maid,slot,stack);
        else maid.getMaidBauble().setStackInSlot(slot,stack);
    }
    private static boolean evolve(EntityMaid maid,int slot,ItemStack expected) {
        var inventory=maid.getMaidBauble();
        if(!(maid.level() instanceof ServerLevel level)||inventory.getStackInSlot(slot)!=expected||!CometBaubles.ordinary(expected))return false;
        // Prepare all data before mutation; there are no events or asynchronous gaps inside this commit.
        var awakened=new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get().builtInRegistryHolder(),1,expected.getComponentsPatch());
        inventory.setStackInSlot(slot,awakened);
        CometRewards.get(level.getServer()).earned(level.getServer(),maid.getOwnerUUID());
        return true;
    }
}
