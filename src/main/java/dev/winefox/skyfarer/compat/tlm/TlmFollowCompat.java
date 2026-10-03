package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class TlmFollowCompat {
    public static void clearGroundMovement(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.setXxa(0); maid.setYya(0); maid.setZza(0);
    }
}
