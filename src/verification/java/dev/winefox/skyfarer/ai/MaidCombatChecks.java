package dev.winefox.skyfarer.ai;

import dev.winefox.skyfarer.compat.spears.SpearHitGeometry;
import dev.winefox.skyfarer.flight.FlightMath;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Uses Minecraft's math types only. Does not create a game, entity, level or launcher. */
public final class MaidCombatChecks {
    public static void main(String[] args) {
        smallTargets();
        passOutcomes();
        withdrawalTiming();
        multiRoute();
        System.out.println("Small-target vertical tolerance, horizontal/range limits, pass deduplication, target switching, withdrawal timing and primary-safe multi-pierce checks passed. Minecraft was not launched.");
    }
    private static void smallTargets() {
        Vec3 from = new Vec3(0, 1.3, -3), to = new Vec3(0, 1.3, 0.5);
        for (double[] size : new double[][]{{1.4, 0.9}, {0.9, 0.5}, {0.4, 0.3}, {0.3, 0.975}}) {
            AABB mob = new AABB(-size[0]/2, 0, -size[0]/2, size[0]/2, size[1], size[0]/2);
            require(SpearHitGeometry.virtualContact(mob, from, to, 0.125, 0).isEmpty(), "Fixture must miss native-sized box");
            require(SpearHitGeometry.virtualContact(mob, from, to, 0.125, 3).isPresent(), "Small mob should be within extended column");
        }
        AABB tiny = new AABB(-0.2, 0, -0.2, 0.2, 0.3, 0.2);
        require(SpearHitGeometry.virtualContact(tiny, new Vec3(0, -2.9, -3), new Vec3(0, -2.9, 0.5), 0.125, 3).isPresent(), "Downward tolerance");
        require(SpearHitGeometry.virtualContact(tiny, new Vec3(0, 3.426, -3), new Vec3(0, 3.426, 0.5), 0.125, 3).isEmpty(), "Reject above 3 blocks plus native margin");
        require(SpearHitGeometry.virtualContact(tiny, new Vec3(0, -3.126, -3), new Vec3(0, -3.126, 0.5), 0.125, 3).isEmpty(), "Reject below 3 blocks plus native margin");
        require(SpearHitGeometry.virtualContact(tiny, new Vec3(0.5, 1.3, -3), new Vec3(0.5, 1.3, 0.5), 0.125, 3).isEmpty(), "Horizontal tolerance must stay native");
        require(SpearHitGeometry.virtualContact(tiny, from, new Vec3(0, 1.3, -1), 0.125, 3).isEmpty(), "No extension of forward reach");
        AABB wall = new AABB(-1, -3, -2, 1, 5, -1);
        Vec3 blocked = wall.clip(from, to).orElseThrow();
        require(SpearHitGeometry.virtualContact(tiny, from, blocked, 0.125, 3).isEmpty(), "A wall-truncated spear cannot reach target behind it");
    }
    private static void passOutcomes() {
        UUID primary = UUID.randomUUID(), other = UUID.randomUUID();
        var history = new SpearPassHistory();
        history.begin(primary); history.finish();
        require(!history.avoided(2).contains(primary), "One missed pass still retries");
        history.begin(primary); history.hit(other); history.finish();
        require(history.avoided(2).contains(primary), "Secondary damage must not disguise a second primary miss");
        history.finish();
        require(!history.avoided(3).contains(primary), "Closing a cycle twice must not double-count its failure");
        history.begin(other); history.hit(other);
        require(history.alreadyHit(other) && !history.hit(other), "Normal and expanded contacts share one successful hit");
        history.hit(primary);
        require(history.alreadyHit(primary) && history.primaryHit(), "Multiple distinct enemies can be pierced in one charge");
        history.finish();
        require(!history.avoided(2).contains(primary), "Actual primary damage clears its failure streak");
        history.begin(other);
        require(!history.alreadyHit(other), "A new pass can hit that enemy again");
        history.finish();
        require(history.avoided(1).contains(other), "Failure limit is configurable");
    }
    private static void withdrawalTiming() {
        require(FlightMath.withdrawalUseTicks(9, 20, 60) == 9, "Tail must not skip windup");
        require(FlightMath.withdrawalUseTicks(45, 20, 60) == 45, "Keep unexpired native charge time");
        require(FlightMath.withdrawalUseTicks(120, 20, 60) == 59, "Long withdrawal remains in native damage window without restarting use");
    }
    private static void multiRoute() {
        Vec3 eye = new Vec3(0, 1.3, -20);
        AABB primaryColumn = new AABB(-0.425, -3, -0.425, 0.425, 5, 0.425);
        require(primaryColumn.clip(eye, new Vec3(0.35, 1.3, 2)).isPresent(), "Aligned secondary route preserves primary contact");
        require(primaryColumn.clip(eye, new Vec3(1.2, 1.3, 2)).isEmpty(), "Reject secondary steering that would skip the primary");
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
