package dev.winefox.skyfarer.compat.spears;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Math-only virtual box; independent of entity loading and NeoForge access transformers. */
public final class SpearHitGeometry {
    private SpearHitGeometry() {}
    public static Optional<Vec3> virtualContact(AABB real, Vec3 from, Vec3 to, double margin, double vertical) {
        var virtualBox = real.inflate(margin, vertical + margin, margin);
        return virtualBox.contains(from) ? Optional.of(from) : virtualBox.clip(from, to);
    }
}
