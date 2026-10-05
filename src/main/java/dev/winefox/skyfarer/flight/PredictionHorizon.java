package dev.winefox.skyfarer.flight;

/** Scales actual prediction steps; default keeps the established flight behavior byte-for-byte. */
public final class PredictionHorizon {
    public static java.util.function.DoubleSupplier multiplier=()->1;
    private PredictionHorizon() {}
    private static final ThreadLocal<Double> snapshot=new ThreadLocal<>();
    public static void enterSnapshot(double value){snapshot.set(value);}
    public static void leaveSnapshot(){snapshot.remove();}
    public static double factor(){Double frozen=snapshot.get();double value=frozen==null?multiplier.getAsDouble():frozen;return Double.isFinite(value)?Math.clamp(value,1,10):1;}
    public static int ticks(int baseline){return (int)Math.ceil(baseline*factor());}
    public static double weight(int tick,int baseline){return tick<baseline?1:.2*baseline/(tick+1.0);}
    public static double futureRisk(int tick,int baseline){return 2000.0/(1+Math.max(0,tick-baseline));}
}
