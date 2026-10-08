package dev.winefox.skyfarer.flight;

import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Controls look direction. Vanilla Elytra/rocket physics gradually follows it; never rotates velocity. */
public final class HeadingController {
    public enum Owner { ROUTE, PROPULSION, SAFETY }
    private Owner owner = Owner.ROUTE;
    private int rejected;
    private Vec3 desired = Vec3.ZERO;
    private double turnAngle;
    private float tickYaw, tickPitch, tickBodyYaw;
    public void beginTick(LivingEntity entity) {
        tickYaw = entity.getYRot(); tickPitch = entity.getXRot(); tickBodyYaw = entity.yBodyRot;
        owner = Owner.ROUTE; rejected = 0;
    }
    public Owner owner() { return owner; }
    public int rejectedCommands() { return rejected; }
    /** Higher authority can preempt, but ordinary same-tick state handoffs cannot steal a motor. */
    private boolean claim(Owner next) {
        if (next.ordinal() < owner.ordinal()) { rejected++; return false; }
        owner = next; return true;
    }
    public void release(Owner previous) { if (owner == previous) owner = Owner.ROUTE; }
    public void steer(LivingEntity entity, Vec3 point, boolean attack, boolean pullUp) {
        if (!claim(Owner.ROUTE)) return;
        desired = point.subtract(entity.getEyePosition()).normalize();
        if (desired.lengthSqr() < 1.0E-6) return;
        Vec3 velocity = entity.getDeltaMovement();
        turnAngle = horizontalAngle(velocity.horizontalDistanceSqr() > 0.01 ? velocity : entity.getLookAngle(), desired);
        float yaw = (float) Math.toDegrees(Math.atan2(-desired.x, desired.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(desired.y, desired.horizontalDistance()));
        float yawRate = (float) (attack ? SkyfarerSettings.attackTurnRate() : SkyfarerSettings.turnRate());
        float pitchRate = (float) (pullUp ? SkyfarerSettings.pullUpPitchRate() : SkyfarerSettings.pitchRate());
        entity.setYRot(Mth.approachDegrees(tickYaw, yaw, yawRate));
        entity.setXRot(Mth.approachDegrees(tickPitch, Mth.clamp(pitch, -60, 60), pitchRate));
        entity.setYHeadRot(entity.getYRot());
        entity.setYBodyRot(Mth.approachDegrees(tickBodyYaw, entity.getYRot(), yawRate));
    }
    public Vec3 desired() { return desired; }
    /** Apply exactly the first step used by the predictor, including explicit emergency limits. */
    public void steerAngles(LivingEntity entity, double yaw, double pitch, double yawRate, double pitchRate) {
        steerAngles(entity, yaw, pitch, yawRate, pitchRate, Owner.ROUTE);
    }
    public void steerAngles(LivingEntity entity, double yaw, double pitch, double yawRate, double pitchRate, Owner authority) {
        if (!claim(authority)) return;
        entity.setYRot((float)FlightPrediction.turn(tickYaw, yaw, yawRate));
        entity.setXRot((float)FlightPrediction.turn(tickPitch, Math.clamp(pitch, -88, 85), pitchRate));
        double p = Math.toRadians(pitch), a = Math.toRadians(yaw);
        desired = new Vec3(-Math.sin(a)*Math.cos(p), -Math.sin(p), Math.cos(a)*Math.cos(p));
        turnAngle = horizontalAngle(entity.getDeltaMovement(), desired);
        entity.setYHeadRot(entity.getYRot());
        entity.setYBodyRot(Mth.approachDegrees(tickBodyYaw, entity.getYRot(), (float)yawRate));
    }
    public double turnAngle() { return turnAngle; }
    public static double angle(Vec3 a, Vec3 b) {
        if (a.lengthSqr() < 1.0E-8 || b.lengthSqr() < 1.0E-8) return 0;
        return Math.toDegrees(Math.acos(Mth.clamp(a.normalize().dot(b.normalize()), -1, 1)));
    }
    public static double horizontalAngle(Vec3 a, Vec3 b) { return angle(new Vec3(a.x, 0, a.z), new Vec3(b.x, 0, b.z)); }
    public static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0, vector.z);
        return flat.lengthSqr() > 1.0E-8 ? flat.normalize() : new Vec3(0, 0, 1);
    }
}
