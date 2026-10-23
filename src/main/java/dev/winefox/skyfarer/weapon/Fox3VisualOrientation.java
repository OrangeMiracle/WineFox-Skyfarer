package dev.winefox.skyfarer.weapon;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Appearance only. Baked model +Z is the white tip, -Z the mechanical exhaust. */
public final class Fox3VisualOrientation {
    public static final double NOZZLE_Z = -0.49238188058026783;
    public record Angles(float yaw, float pitch) {}
    public static Angles fromVelocity(Vec3 v, float oldYaw, float oldPitch) {
        if (v.lengthSqr() < 1.0e-8) return new Angles(oldYaw, oldPitch);
        double h = Math.hypot(v.x, v.z);
        return new Angles(h < 1.0e-6 ? oldYaw : (float)Math.toDegrees(Math.atan2(v.x, v.z)),
            (float)-Math.toDegrees(Math.atan2(v.y, h)));
    }
    public static Quaternionf rotation(float yaw, float pitch) {
        return new Quaternionf().rotationY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch));
    }
    public static Vec3 nozzle(float yaw, float pitch) {
        var p = rotation(yaw,pitch).transform(new Vector3f(0,0,(float)NOZZLE_Z));
        return new Vec3(p.x,p.y,p.z);
    }
    private Fox3VisualOrientation() {}
}
