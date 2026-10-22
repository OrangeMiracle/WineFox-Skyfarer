package dev.winefox.skyfarer.weapon;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared native segment geometry: only a point on this tick's swept segment can be an impact. */
public final class Fox3Impact {
    private Fox3Impact() {}

    public static Vec3 nearest(Vec3 start, Vec3 end, Vec3 blockHit, Iterable<AABB> obstacles) {
        Vec3 closest = blockHit;
        double best = closest == null ? start.distanceToSqr(end) + 1.0e-8 : start.distanceToSqr(closest);
        for (AABB obstacle : obstacles) {
            Vec3 point = obstacle.contains(start) ? start : obstacle.clip(start, end).orElse(null);
            if (point != null && start.distanceToSqr(point) < best) { best = start.distanceToSqr(point); closest = point; }
        }
        return closest;
    }
}
