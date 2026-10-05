package dev.winefox.skyfarer.flight;

/** Frozen 1.21.1 entity physics, with no world/entity references or invented drag attributes. */
public record PhysicsContext(double gravity, int slowFallingTicks) {
    public static final PhysicsContext VANILLA = new PhysicsContext(.08, 0);
    public PhysicsContext {
        if (!Double.isFinite(gravity)) gravity = .08;
        slowFallingTicks = Math.max(0, slowFallingTicks);
    }
    public double travelGravity(double verticalVelocity) {
        return verticalVelocity <= 0 && slowFallingTicks > 0 ? Math.min(gravity, .01) : gravity;
    }
    public PhysicsContext nextTick() {
        return slowFallingTicks == 0 || slowFallingTicks == Integer.MAX_VALUE ? this
            : new PhysicsContext(gravity, slowFallingTicks - 1);
    }
    public PhysicsContext after(long ticks) {
        return slowFallingTicks==0||slowFallingTicks==Integer.MAX_VALUE?this:
            new PhysicsContext(gravity,(int)Math.max(0,slowFallingTicks-Math.max(0,ticks)));
    }
}
