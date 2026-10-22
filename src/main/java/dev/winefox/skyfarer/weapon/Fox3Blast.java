package dev.winefox.skyfarer.weapon;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Predicate;

/** One burst, one UUID hit: protection/radius precede LOS work, and LOS precedes damage. */
public final class Fox3Blast {
    private Fox3Blast() {}

    public static <T> int apply(Iterable<T> candidates, Vec3 origin, double radius, double damage,
                               Function<T, UUID> identity, Predicate<T> legal, Function<T, AABB> bounds,
                               Predicate<T> visible, ObjDoubleConsumer<T> hurt) {
        if (!(damage > 0) || !(radius > 0) || !Double.isFinite(damage) || !Double.isFinite(radius)) return 0;
        var hit = new HashSet<UUID>();
        int count = 0;
        for (T victim : candidates) {
            if (!hit.add(identity.apply(victim)) || !legal.test(victim) || !within(origin, bounds.apply(victim), radius) || !visible.test(victim)) continue;
            hurt.accept(victim, damage);
            count++;
        }
        return count;
    }

    public static boolean within(Vec3 origin, AABB box, double radius) {
        Vec3 nearest = new Vec3(Math.clamp(origin.x, box.minX, box.maxX), Math.clamp(origin.y, box.minY, box.maxY), Math.clamp(origin.z, box.minZ, box.maxZ));
        return origin.distanceToSqr(nearest) <= radius * radius;
    }
}
