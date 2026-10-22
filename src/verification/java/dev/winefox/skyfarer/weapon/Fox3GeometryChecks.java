package dev.winefox.skyfarer.weapon;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Executes the production impact and blast gates with real mapped MC segment/AABB geometry. */
public final class Fox3GeometryChecks {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void point(Vec3 actual, Vec3 expected, String message) { check(actual != null && actual.distanceToSqr(expected) < 1e-12, message + ": " + actual); }

    public static void main(String[] args) {
        collision();
        blast();
        System.out.println("Fox3 impact/blast passed: swept thin walls/entities, nearest hit and initial containment; spherical radius, native AABB LOS, protected/occluded filtering, UUID dedup and configured damage");
    }

    private static void collision() {
        Vec3 start = Vec3.ZERO, end = new Vec3(20, 0, 0);
        AABB thin = new AABB(5, -1, -1, 5.01, 1, 1);
        Vec3 wall = thin.clip(start, end).orElseThrow();
        point(Fox3Impact.nearest(start, end, wall, List.of(new AABB(10, -0.3, -0.3, 11, 1, 0.3))), new Vec3(5, 0, 0), "entity behind wall won");
        point(Fox3Impact.nearest(start, end, wall, List.of(new AABB(2, -0.3, -0.3, 3, 1, 0.3))), new Vec3(2, 0, 0), "closer entity skipped");
        point(Fox3Impact.nearest(start, end, null, List.of(new AABB(0.5, -0.3, -0.3, 0.51, 1, 0.3))), new Vec3(0.5, 0, 0), "swept entity tunneling");
        point(Fox3Impact.nearest(start, end, null, List.of(new AABB(-1, -1, -1, 1, 1, 1))), start, "initial overlap not immediate");
        check(Fox3Impact.nearest(start, end, null, List.of(new AABB(2, 1, 1, 3, 2, 2))) == null, "off-path obstacle hit");
        check(Fox3Impact.nearest(start, new Vec3(1, 0, 0), null, List.of(thin)) == null, "obstacle beyond tick segment hit early");
        // Two sides of a wing use exactly the same sweep rule, including negative and vertical motion.
        point(Fox3Impact.nearest(new Vec3(0, 5, 0), new Vec3(0, -5, 0), null,
            List.of(new AABB(-1, 1, -1, 1, 1.01, 1))), new Vec3(0, 1.01, 0), "descending roof collision");
    }

    private record Victim(UUID id, AABB bounds, boolean legal) {}
    private static Victim victim(int id, AABB bounds, boolean legal) { return new Victim(new UUID(0, id), bounds, legal); }

    private static void blast() {
        Vec3 origin = Vec3.ZERO;
        Victim visible = victim(1, new AABB(-1.5, -0.2, -0.2, -1, 1, 0.2), true);
        Victim duplicate = new Victim(visible.id(), visible.bounds(), true);
        Victim protectedFriend = victim(2, new AABB(-0.2, -0.2, 0.8, 0.2, 1, 1.2), false);
        Victim occluded = victim(3, new AABB(1, -0.2, -0.2, 1.5, 1, 0.2), true);
        Victim cornerOutside = victim(4, new AABB(1.8, -0.1, 1.8, 2, 1, 2), true);
        Victim touchingRadius = victim(5, new AABB(-0.2, -0.2, -3, 0.2, 1, -2), true);
        List<Victim> candidates = List.of(visible, duplicate, protectedFriend, occluded, cornerOutside, touchingRadius);
        AABB wall = new AABB(0.4, -2, -2, 0.6, 2, 2);
        Map<UUID, Double> damage = new HashMap<>();
        Set<UUID> losChecked = new HashSet<>();
        int hits = Fox3Blast.apply(candidates, origin, 2, 5, Victim::id, Victim::legal, Victim::bounds, target -> {
            check(losChecked.add(target.id()), "duplicate target repeated LOS");
            return wall.clip(origin, target.bounds().getCenter()).isEmpty();
        }, (target, amount) -> { check(damage.put(target.id(), amount) == null, "duplicate UUID damage"); });
        check(hits == 2 && damage.equals(Map.of(visible.id(), 5.0, touchingRadius.id(), 5.0)), "radius/protection/LOS/damage: " + damage);
        check(!losChecked.contains(protectedFriend.id()) && !losChecked.contains(cornerOutside.id()), "protected/outside entity consumed LOS work");
        check(Fox3Blast.within(origin, new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5), 0.1), "containing body not in blast");
        int configured = Fox3Blast.apply(candidates, origin, 1.1, 2.5, Victim::id, Victim::legal, Victim::bounds,
            target -> wall.clip(origin, target.bounds().getCenter()).isEmpty(), (target, amount) -> check(amount == 2.5, "custom damage ignored"));
        check(configured == 1, "configured smaller radius ignored");
        check(Fox3Blast.apply(candidates, origin, 2, 0, Victim::id, Victim::legal, Victim::bounds,
            target -> { throw new AssertionError("zero damage consumed LOS work"); }, (target, amount) -> { throw new AssertionError("zero damage hurt"); }) == 0, "zero damage not visual-only");
    }
}
