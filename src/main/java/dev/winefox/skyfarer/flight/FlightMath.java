package dev.winefox.skyfarer.flight;

/** Pure arithmetic, also exercised by the compile-only verification task. */
public final class FlightMath {
    private FlightMath() {}
    public static boolean chargeDue(double alongDistance, double speed, int delay) {
        return alongDistance / Math.max(0.25, speed) <= delay + 8;
    }
    public static boolean passed(double alongDistance, double reach) { return alongDistance < -Math.max(2, reach); }
    public static int withdrawalUseTicks(int actual, int delay, int window) {
        // An unready weapon keeps its real windup; an expired one retains its last valid damage tick.
        return Math.min(actual, Math.max(delay, window - 1));
    }
}
