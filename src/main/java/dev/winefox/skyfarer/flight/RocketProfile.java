package dev.winefox.skyfarer.flight;

public final class RocketProfile {
    private RocketProfile() {}
    public static boolean active(int tick,int remaining,FlightPrediction.State start) {
        return tick>=start.rocketDelay() && tick<remaining+start.rocketDelay();
    }
    public static int remaining(int tick,int remaining,FlightPrediction.State start) {
        return Math.max(0,remaining-Math.max(0,tick-start.rocketDelay()));
    }
    public static int minimumTicks(int duration) { return 10 * (1 + duration) + 1; }
    public static int maximumTicks(int duration) { return minimumTicks(duration) + 11; }
    // The rocket expires after life > lifetime; two uniform random terms have mean 5.5.
    public static double expectedTicks(int duration) { return minimumTicks(duration) + 5.5; }
    public static double score(int duration, int wantedTicks, boolean explosive) {
        double ticks = expectedTicks(duration);
        return Math.abs(ticks - wantedTicks) + Math.max(0, ticks - wantedTicks) * 0.7 + (explosive ? 1000 : 0);
    }
}
