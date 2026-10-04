package dev.winefox.skyfarer.command;

/** World-independent command decisions, also exercised by offline verification. */
public final class DispatchPolicy {
    public static final double HANDOFF_RANGE=48;
    private DispatchPolicy() {}
    public static boolean admitted(boolean home,boolean creative,double ownerDistance,double homeRange) {
        return !home||creative||ownerDistance<=homeRange;
    }
    public static boolean transit(boolean targetLoaded,double distance) {return !targetLoaded||distance>HANDOFF_RANGE;}
    public static boolean retainCreative(boolean creative,boolean marked,boolean alive,boolean loaded) {
        return creative&&marked&&alive&&loaded;
    }
    public static double score(double bonus,double distance,double angle,boolean previous,double route) {
        return bonus-distance*.04-angle*.25+(previous?20:0)+route;
    }
}
