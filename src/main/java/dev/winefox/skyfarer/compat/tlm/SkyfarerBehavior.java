package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import java.util.Map;

public final class SkyfarerBehavior extends Behavior<EntityMaid> {
    public SkyfarerBehavior() { super(Map.of(), 1); }
    @Override protected void start(ServerLevel level, EntityMaid maid, long time) { SkyfarerRuntime.workTick(maid); }
}
