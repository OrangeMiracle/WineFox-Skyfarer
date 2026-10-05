package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** Planning metric only. A positive reserve is NEVER a reachability certificate. */
public final class EnergyReserveEnvelope {
    public enum Stage { APPROACH, STRIKE, EXIT }
    public record Sample(int tick,Stage stage,double requiredHeight,double minimumSpeed,double reserve) {}
    public record Result(double worstReserve,int worstTick,List<Sample> samples) {
        public Result { samples=List.copyOf(samples); }
    }
    private final ArrayList<Sample> samples=new ArrayList<>();
    private final boolean recordSamples;
    public EnergyReserveEnvelope(){this(true);}
    public EnergyReserveEnvelope(boolean recordSamples){this.recordSamples=recordSamples;}
    private double worst=Double.POSITIVE_INFINITY;
    private int worstTick=-1;
    public void observe(FlightPrediction.State state,int tick,Stage stage,double requiredHeight,double minimumSpeed,double gravity) {
        double reserve=GlidePlanner.energy(state,requiredHeight,gravity)-.5*minimumSpeed*minimumSpeed;
        if(recordSamples)samples.add(new Sample(tick,stage,requiredHeight,minimumSpeed,reserve));
        if(reserve<worst){worst=reserve;worstTick=tick;}
    }
    public Result result(){return new Result(worst,worstTick,samples);}
    /** Highest remaining requirement, not the ground below or absolute world altitude. */
    public static double remainingHeight(double approach,double strike,GlidePlanner.Exit exit,boolean exiting) {
        return exiting?Math.max(approach,exit==null?approach:exit.y()):Math.max(approach,Math.max(strike,exit==null?strike:exit.y()));
    }
}
