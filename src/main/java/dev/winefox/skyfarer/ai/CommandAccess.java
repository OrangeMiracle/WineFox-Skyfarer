package dev.winefox.skyfarer.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Function;

/** Server installs order access; offline combat replays keep the empty implementation. */
public final class CommandAccess {
    public static Function<EntityMaid,List<LivingEntity>> targets = maid -> List.of();
    public static Function<EntityMaid,Vec3> anchor = maid -> null;
    // Independent of the candidate list: attackability queries must not recurse through targets().
    public static java.util.function.BiPredicate<EntityMaid,LivingEntity> designated = (maid,target)->false;
    public static boolean marked(EntityMaid maid,LivingEntity target) {
        return target!=null && targets.apply(maid).contains(target);
    }
    private CommandAccess() {}
}
