package dev.winefox.skyfarer.compat.spears;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.TargetEvaluator;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.safety.AirCollisionChecker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Predicate;

/** Virtual vertical tolerance for one maid spear query; never changes any entity dimensions. */
public final class MaidSpearCollisions {
    private MaidSpearCollisions() {}
    public static Collection<EntityHitResult> extend(EntityMaid maid, float minReach, float maxReach, float margin,
        Predicate<Entity> predicate, Collection<EntityHitResult> nativeHits) {
        double vertical = SkyfarerSettings.get("spear_vertical_expansion");
        if (vertical <= 0) return nativeHits;
        var hits = new LinkedHashMap<UUID, EntityHitResult>();
        nativeHits.forEach(hit -> hits.putIfAbsent(hit.getEntity().getUUID(), hit));
        Vec3 eye = maid.getEyePosition();
        Vec3 direction = maid.calculateViewVector(maid.getXRot(), maid.getYHeadRot());
        Vec3 from = eye.add(direction.scale(minReach));
        Vec3 to = eye.add(direction.scale(maxReach + Math.max(0, maid.getKnownMovement().dot(direction))));
        if (!AirCollisionChecker.loaded(maid, eye, to)) return hits.values();
        var block = maid.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        if (eye.distanceToSqr(to) < eye.distanceToSqr(from)) return hits.values();
        AABB query = new AABB(from, to).inflate(margin, vertical + margin, margin);
        for (LivingEntity target : maid.level().getEntitiesOfClass(LivingEntity.class, query)) {
            if (hits.containsKey(target.getUUID()) || !TargetEvaluator.attackable(maid, target) || !predicate.test(target)
                || !maid.level().getWorldBorder().isWithinBounds(target.blockPosition())) continue;
            var intersection = SpearHitGeometry.virtualContact(target.getBoundingBox(), from, to, margin, vertical);
            if (intersection.isEmpty()) continue;
            Vec3 point = intersection.get();
            // Connect the virtual contact to the nearest real surface, preventing hits through floors/walls.
            AABB real = target.getBoundingBox();
            Vec3 surface = new Vec3(Math.clamp(point.x, real.minX, real.maxX), Math.clamp(point.y, real.minY, real.maxY), Math.clamp(point.z, real.minZ, real.maxZ));
            if (!AirCollisionChecker.clear(maid, point, surface)) continue;
            hits.putIfAbsent(target.getUUID(), new EntityHitResult(target, surface));
        }
        return hits.values();
    }
}
