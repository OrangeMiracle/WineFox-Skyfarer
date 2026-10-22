package dev.winefox.skyfarer.weapon;

/** Pure, bounded missile steering. All distances are blocks, time is server ticks. */
public final class Fox3Guidance {
    public static final int CLIMB_TICKS = 12;
    public static final int MIN_ROLLOVER_TICKS = 4;
    public static final int MAX_ROLLOVER_TICKS = 24;
    public static final int MAX_LIFE_TICKS = 160;
    public static final int RETARGET_TICKS = 20;
    public static final double LAUNCH_SPEED = 0.85;
    public static final double MAX_SPEED = 1.10;
    public static final double MIN_TURN_RADIUS = 5.0;
    public static final double MAX_TURN_RADIANS = Math.toRadians(6.0);
    public static final double ROLLOVER_TURN_RADIANS = Math.toRadians(12.0);

    /** Local weapon phases only; never part of the maid's air-control state machine. */
    public enum Phase { CLIMB, ROLLOVER, TRACK }

    private Fox3Guidance() {}

    public record Vector(double x, double y, double z) {
        public Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        public Vector scale(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Vector unit() { double length = length(); return length > 1.0e-9 && Double.isFinite(length) ? scale(1.0 / length) : new Vector(0, 1, 0); }
    }

    /** Side -1 / +1 gives two outward launch directions, independent of the maid's pitch. */
    public static Vector launchVelocity(double forwardX, double forwardZ, double side) {
        double horizontal = Math.hypot(forwardX, forwardZ);
        double x = horizontal > 1.0e-8 ? forwardX / horizontal : 0;
        double z = horizontal > 1.0e-8 ? forwardZ / horizontal : 1;
        double spread = Math.clamp(side, -1.5, 1.5) * 0.58;
        return new Vector(x * 0.18 - z * spread, 1.0, z * 0.18 + x * spread).unit().scale(LAUNCH_SPEED);
    }

    /** A short outward bend precedes the faster rollover; every transition still uses turnTowards. */
    public static Vector climbDirection(Vector launchVelocity, int age) {
        double progress = Math.clamp(age / (double)CLIMB_TICKS, 0.0, 1.0);
        Vector horizontal = new Vector(launchVelocity.x, 0, launchVelocity.z);
        return new Vector(horizontal.x, Math.max(0.28, launchVelocity.y * (1.0 - 0.65 * progress)), horizontal.z).unit();
    }

    public static Vector step(Vector velocity, Vector desiredDirection) {
        return step(velocity, desiredDirection, Phase.TRACK);
    }

    public static Phase nextPhase(Phase phase, int age, Vector velocity, Vector rolloverDirection) {
        if (phase == Phase.CLIMB) return age > CLIMB_TICKS ? Phase.ROLLOVER : Phase.CLIMB;
        if (phase == Phase.TRACK) return Phase.TRACK;
        int elapsed = age - CLIMB_TICKS;
        boolean downwardAndAligned = velocity.unit().y < -0.1 && velocity.unit().dot(rolloverDirection.unit()) >= Math.cos(Math.toRadians(25));
        return elapsed >= MAX_ROLLOVER_TICKS || elapsed >= MIN_ROLLOVER_TICKS && downwardAndAligned ? Phase.TRACK : Phase.ROLLOVER;
    }

    public static Vector step(Vector velocity, Vector desiredDirection, Phase phase) {
        double speed = Math.min(MAX_SPEED, Math.max(LAUNCH_SPEED, velocity.length()) + 0.025);
        // The polygon's circumradius is s/(2 sin(theta/2)); keep it >= five blocks.
        double phaseLimit = phase == Phase.ROLLOVER ? ROLLOVER_TURN_RADIANS : MAX_TURN_RADIANS;
        double turn = Math.min(phaseLimit, 2.0 * Math.asin(Math.min(1.0, speed / (2.0 * MIN_TURN_RADIUS))));
        return turnTowards(velocity, desiredDirection, turn).scale(speed);
    }

    public static Vector turnTowards(Vector current, Vector desired, double maxAngle) {
        Vector from = current.unit();
        Vector to = desired.length() > 1.0e-9 ? desired.unit() : from;
        double cosine = Math.clamp(from.dot(to), -1.0, 1.0);
        double angle = Math.acos(cosine);
        if (angle <= Math.max(0, maxAngle)) return to;
        Vector tangent = to.add(from.scale(-cosine));
        if (tangent.length() < 1.0e-8) {
            // Deterministic perpendicular for exact U-turns, without snapping or NaNs.
            Vector axis = Math.abs(from.y) < 0.8 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
            tangent = axis.add(from.scale(-from.dot(axis)));
        }
        double applied = Math.max(0, maxAngle);
        return from.scale(Math.cos(applied)).add(tangent.unit().scale(Math.sin(applied))).unit();
    }
}
