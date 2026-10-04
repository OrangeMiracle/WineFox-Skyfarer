package dev.winefox.skyfarer.flight;

/** A smooth-bottom descent needs both gravitational fall time and attitude transition time. */
public final class DiveProfile {
    private DiveProfile() {}
    public static double minimumDistance(double rise,double speed,double gravity) {
        return Math.max(12,speed*(Math.sqrt(2*Math.max(0,rise-2)/Math.max(.01,gravity))+8));
    }
    public static double slope(double rise,double distance) { return rise*(distance+8)/(distance*distance); }
}
