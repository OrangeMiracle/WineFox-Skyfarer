package dev.winefox.skyfarer.weapon;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Fox3 has no access to strategic marks, target ranking, controllers, or the flight planner. */
public final class Fox3Targets {
    public static final int MAX_CANDIDATES = 128;
    public static final double MAX_RANGE = 64, OWNER_RADIUS = 16;
    private Fox3Targets() {}
    public static boolean legal(EntityMaid maid, LivingEntity target) {
        var owner = maid.getOwner();
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == maid.level()
            && target != null && target != maid && target != owner && target instanceof Enemy
            && !(target instanceof Player) && !(target instanceof EntityMaid)
            && !(target instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
            && target.level() == maid.level() && target.isAlive() && !target.isRemoved()
            && !target.isSpectator() && !target.isInvulnerable()
            && !maid.isAlliedTo(target) && !target.isAlliedTo(maid)
            && !owner.isAlliedTo(target) && !target.isAlliedTo(owner) && maid.canAttack(target);
    }
    public static double range(EntityMaid maid) {
        return Math.min(MAX_RANGE, Math.max(SkyfarerSettings.combatRange(), maid.isHomeModeEnable() ? maid.getRestrictRadius() : 0));
    }
    /** Null means budget deferred, empty means a completed scan found no legal target. */
    public static List<LivingEntity> scan(EntityMaid maid, Vec3 anchor) {
        return scan(maid, anchor, maid.position());
    }
    /** In-flight reacquisition follows the missile's locality even while its launcher flies away. */
    public static List<LivingEntity> scan(EntityMaid maid, Vec3 anchor, Vec3 origin) {
        if (!Fox3System.searchBudget(maid)) return null;
        double radius = range(maid);
        var candidates = new ArrayList<LivingEntity>();
        // Cap raw candidates before filtering so an area full of protected entities is bounded too.
        maid.level().getEntities(EntityTypeTest.forClass(LivingEntity.class), new AABB(anchor, anchor).inflate(radius),
            entity -> true, candidates, MAX_CANDIDATES);
        candidates.removeIf(entity -> !legal(maid, entity) || entity.position().distanceToSqr(anchor) > radius * radius
            || entity.position().distanceToSqr(origin) > MAX_RANGE * MAX_RANGE);
        return candidates;
    }
    public static LivingEntity choose(EntityMaid maid, List<LivingEntity> candidates, Map<UUID, Integer> assignments) {
        if (candidates == null || candidates.isEmpty()) return null;
        var owner = maid.getOwner();
        var legal = candidates.stream().filter(entity -> legal(maid, entity)).toList();
        var nearby = legal.stream().filter(entity -> owner != null && entity.distanceToSqr(owner) <= OWNER_RADIUS * OWNER_RADIUS).toList();
        var pool = nearby.isEmpty() ? legal : nearby;
        int least = pool.stream().mapToInt(entity -> assignments.getOrDefault(entity.getUUID(), 0)).min().orElse(0);
        var dispersed = pool.stream().filter(entity -> assignments.getOrDefault(entity.getUUID(), 0) == least).toList();
        return dispersed.isEmpty() ? null : dispersed.get(maid.getRandom().nextInt(dispersed.size()));
    }
}
