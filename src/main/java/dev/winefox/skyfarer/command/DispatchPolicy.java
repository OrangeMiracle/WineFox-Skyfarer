package dev.winefox.skyfarer.command;

/** World-independent command decisions, also exercised by offline verification. */
public final class DispatchPolicy {
    public static final double HANDOFF_RANGE=48,CREATIVE_HANDOFF_RANGE=32;
    public static double handoffRange(boolean creative){return creative?CREATIVE_HANDOFF_RANGE:HANDOFF_RANGE;}
    private DispatchPolicy() {}
    public static boolean admitted(boolean home,boolean creative,double ownerDistance,double homeRange) {
        return !home||creative||ownerDistance<=homeRange;
    }
    public static boolean admitted(boolean home,boolean creative,double distance,double homeRange,double creativeRange){
        return creative?(creativeRange==0||distance<=creativeRange):admitted(home,false,distance,homeRange);
    }
    public static boolean transit(boolean targetLoaded,double distance) {return !targetLoaded||distance>HANDOFF_RANGE;}
    /** Persisted work-mode hints cannot veto discovery; the loaded entity supplies authority. */
    public static boolean discover(boolean known,boolean home,boolean creative,boolean survival,double distance,double homeRange,double creativeRange){
        return creative&&admitted(home,true,distance,homeRange,creativeRange)
            ||survival&&(distance<=homeRange||known&&!home);
    }
    public static boolean transit(boolean targetLoaded,double distance,boolean creative){return !targetLoaded||distance>handoffRange(creative);}
    public static boolean retainCreative(boolean creative,boolean marked,boolean alive,boolean loaded) {
        return creative&&marked&&alive&&loaded;
    }
    public static boolean refreshOrder(boolean transit,boolean finishing,boolean marksChanged,boolean committed){return !transit&&!committed&&(finishing||marksChanged);}
    public static double score(double bonus,double distance,double angle,boolean previous,double route) {
        return bonus-distance*.04-angle*.25+(previous?20:0)+route;
    }
}
