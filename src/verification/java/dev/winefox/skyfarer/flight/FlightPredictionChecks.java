package dev.winefox.skyfarer.flight;

/** Numerical flight scenarios only. No Minecraft classes, launcher, assets or world. */
public final class FlightPredictionChecks {
    public static void main(String[] args) {
        require(RocketProfile.minimumTicks(0)==11 && RocketProfile.maximumTicks(3)==52,"native duration bounds");
        for(int duration=0;duration<=3;duration++)require(RocketProfile.maximumTicks(duration)-RocketProfile.minimumTicks(duration)==11,"all twelve random lifetimes remain possible");
        int failures = 0;
        for (double speed : new double[]{0.8, 1.2, 1.7}) for (int rocket : new int[]{0, 1, 8, 20, 26, 31, 40, 46, 51})
            for (double height : new double[]{1.3, 5.3}) for (int rescue : new int[]{20, 26, 31}) if (!pass(speed, rocket, height, rescue)) failures++;
        require(failures == 0, failures + " intercept scenarios failed");
        for (double speed : new double[]{0.8, 1.2, 1.7}) setupTurn(speed);
        System.out.println("162 powered/unpowered flight scenarios and rocket selection passed; Minecraft was not launched.");
    }
    private static void setupTurn(double speed) {
        var s = new FlightPrediction.State(0, 5.3, 0, 0, 0, speed, 0, 0);
        boolean outbound = true; int rocket = 0;
        for (int tick = 0; tick < 220; tick++) {
            if (outbound && s.z() >= 24) outbound = false;
            double targetZ = outbound ? 28 : -8;
            double yaw = Math.toDegrees(Math.atan2(s.x(), targetZ - s.z()));
            double lookError = Math.abs(FlightPrediction.turn(s.yaw(), yaw, 360) - s.yaw());
            double velocityYaw = Math.toDegrees(Math.atan2(-s.vx(), s.vz()));
            double velocityError = Math.abs(FlightPrediction.turn(velocityYaw, yaw, 360) - velocityYaw);
            if (!outbound && Math.max(lookError, velocityError) < 8 && Math.hypot(s.x(), targetZ - s.z()) > 18) return;
            if (Math.hypot(s.vx(), s.vz()) < 0.8 && lookError < 20 && rocket <= tick && s.pitch() <= 0) rocket = tick + 26;
            var choice = FlightPrediction.choose(s, yaw, 5.3, 0, 0, 14, 1.15, Math.max(0, rocket - tick), 5, 3, 4, 0.08);
            s = FlightPrediction.step(s, yaw, choice.pitch(), 5, 3, 4, 0.08, tick < rocket);
        }
        throw new AssertionError("Fixed exit did not escape opposite-heading setup at speed " + speed);
    }
    private static boolean pass(double speed, int rocket, double height, int rescueDuration) {
        double distance = Math.max(32, speed * 24), slope = (height - 1.3) / (distance - 14);
        var s = new FlightPrediction.State(0, height, -distance, 0, 0, speed, 0, 0);
        double minimum = height;
        for (int tick = 0; tick < 100; tick++) {
            if (rocket <= tick && Math.hypot(s.vx(), s.vz()) < 0.95 && s.pitch() <= 0 && s.y() < 2.5) rocket = tick + rescueDuration;
            var choice = FlightPrediction.choose(s, 0, 1.3, -s.z(), slope, 14, 1.15,
                Math.max(0, rocket - tick), 5, 3, 4, 0.08);
            s = FlightPrediction.step(s, 0, choice.pitch(), 5, 3, 4, 0.08, tick < rocket);
            minimum = Math.min(minimum, s.y());
            if (s.z() >= -1) {
                boolean success = minimum > 1.05 && s.y() <= 1.85 && Math.abs(s.vy()) < 0.25;
                if (!success) System.out.printf("FAIL speed=%.1f rocket=%d height=%.1f -> y=%.3f vy=%.3f pitch=%.1f minimum=%.3f%n", speed, rocket, height, s.y(), s.vy(), s.pitch(), minimum);
                return success;
            }
        }
        return false;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
