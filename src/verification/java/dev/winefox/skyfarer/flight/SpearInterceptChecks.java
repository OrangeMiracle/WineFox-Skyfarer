package dev.winefox.skyfarer.flight;

/** Native-sized spear segment versus a moving mob box; no damage calls or game launch. */
public final class SpearInterceptChecks {
    public static void main(String[] args) {
        int scenarios = 0, failed = 0;
        for (double speed : new double[]{0.8, 1.2, 1.7}) for (int rocket : new int[]{0, 26, 46})
            for (double height : new double[]{1.3, 5.3}) for (double drift : new double[]{0, 12})
                for (double targetSpeed : new double[]{0, 0.1}) for (int delay : new int[]{10, 20}) {
                    scenarios++;
                    if (!pass(speed, rocket, height, drift, targetSpeed, delay)) failed++;
                }
        if (failed > 0) throw new AssertionError(failed + "/" + scenarios + " spear intercept scenarios missed");
        System.out.println(scenarios + " native-sized spear intercept scenarios passed; Minecraft was not launched.");
    }
    private static boolean pass(double speed, int rocket, double height, double drift, double targetSpeed, int delay) {
        double distance = Math.max(32, 1.7 * (delay + 10)), slope = (height - 1.3) / (distance - 14);
        var s = new FlightPrediction.State(0, height, -distance, -speed * Math.sin(Math.toRadians(drift)), 0,
            speed * Math.cos(Math.toRadians(drift)), 0, 0);
        int chargeStart = -1; boolean hit = false; double minY = height;
        for (int tick = 0; tick < 100 && s.z() < 3; tick++) {
            double forwardSpeed = Math.max(rocket > tick ? 1.7 : 0.6, Math.hypot(s.vx(), s.vz()));
            double along = -s.z(), lead = Math.clamp((along - 2.25) / forwardSpeed, 0, 12);
            double yaw = along <= 0 ? 0 : Math.toDegrees(Math.atan2(s.x() - targetSpeed * (tick + lead), -s.z()));
            if (rocket <= tick && Math.hypot(s.vx(), s.vz()) < 0.95 && s.pitch() <= 0 && s.y() < 2.5) rocket = tick + 26;
            var aim = new FlightPrediction.Aim(targetSpeed * tick, 0, 0, 0.3, 1.95, targetSpeed, 0, 0,
                1, 2.25, 0.125, chargeStart < 0 ? delay : Math.max(0, delay - (tick - chargeStart)));
            var choice = FlightPrediction.choose(s, yaw, 1.3, Math.max(0, along), slope, 14, 1.15,
                Math.max(0, rocket - tick), 2.5, 3, 4, 0.08, along > 0 ? aim : null);
            var aimed = new FlightPrediction.State(s.x(), s.y(), s.z(), s.vx(), s.vy(), s.vz(),
                FlightPrediction.turn(s.yaw(), yaw, 2.5), FlightPrediction.turn(s.pitch(), choice.pitch(), choice.pitch() < s.pitch() ? 4 : 3));
            if (Boolean.getBoolean("wfs.trace") && speed == 1.7 && height == 5.3 && drift == 12 && targetSpeed == 0 && delay == 10 && rocket == 0 && along < 14)
                System.out.printf("TRACE tick=%d z=%.2f y=%.2f pitch=%.2f chosen=%.2f using=%d miss=%.3f%n", tick, s.z(), s.y(), s.pitch(), choice.pitch(), chargeStart < 0 ? -1 : tick-chargeStart, aim.missSquared(aimed, 0));
            if (chargeStart >= 0 && tick - chargeStart >= delay && nativeSegmentHits(aimed, targetSpeed * tick)) hit = true;
            if (chargeStart < 0 && FlightMath.chargeDue(along, Math.max(1.7, forwardSpeed), delay)) chargeStart = tick;
            s = FlightPrediction.step(s, yaw, choice.pitch(), 2.5, 3, 4, 0.08, tick < rocket);
            minY = Math.min(minY, s.y());
        }
        boolean ok = hit && minY >= 1.05;
        if (!ok) System.out.printf("MISS speed=%.1f rocketEnd=%d height=%.1f drift=%.0f moving=%.1f delay=%d hit=%s minY=%.2f x=%.2f y=%.2f z=%.2f%n",
            speed, rocket, height, drift, targetSpeed, delay, hit, minY, s.x(), s.y(), s.z());
        return ok;
    }
    // Independent slab clipping, rather than asserting the predictor's own scoring function.
    private static boolean nativeSegmentHits(FlightPrediction.State s, double targetX) {
        double p = Math.toRadians(s.pitch()), y = Math.toRadians(s.yaw());
        double[] direction = {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
        double[] origin = {s.x(), s.y(), s.z()};
        double[] lower = {targetX - 0.425, -0.125, -0.425}, upper = {targetX + 0.425, 2.075, 0.425};
        double near = 1, far = 2.25 + Math.max(0, s.vx() * direction[0] + s.vy() * direction[1] + s.vz() * direction[2]);
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1e-9) {
                if (origin[axis] < lower[axis] || origin[axis] > upper[axis]) return false;
            } else {
                double a = (lower[axis] - origin[axis]) / direction[axis], b = (upper[axis] - origin[axis]) / direction[axis];
                near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
                if (near > far) return false;
            }
        }
        return true;
    }
}
