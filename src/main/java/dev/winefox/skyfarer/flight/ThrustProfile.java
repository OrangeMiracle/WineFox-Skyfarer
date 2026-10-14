package dev.winefox.skyfarer.flight;

/** Pure numeric force contract shared by certification and execution. No rocket force/speed floor. */
public record ThrustProfile(PropulsionDemand.Source source,float acceleration,double targetSpeed) {
    public ThrustProfile(PropulsionDemand.Source source,float acceleration){this(source,acceleration,0);}
    public static ThrustProfile cruise(double minimum){return new ThrustProfile(PropulsionDemand.Source.VIRTUAL_PROPULSION,.2f,Math.clamp(minimum+.2,1.2,1.8));}
    public static final ThrustProfile RIPTIDE=new ThrustProfile(PropulsionDemand.Source.RIPTIDE,.2f);
    public static final java.util.List<ThrustProfile> VIRTUAL=java.util.List.of(
        new ThrustProfile(PropulsionDemand.Source.VIRTUAL_PROPULSION,.04f),
        new ThrustProfile(PropulsionDemand.Source.VIRTUAL_PROPULSION,.10f),
        new ThrustProfile(PropulsionDemand.Source.VIRTUAL_PROPULSION,.20f));
    public ThrustProfile {
        if(source!=PropulsionDemand.Source.RIPTIDE&&source!=PropulsionDemand.Source.VIRTUAL_PROPULSION
            ||!Float.isFinite(acceleration)||acceleration<=0||acceleration>.2f||!Double.isFinite(targetSpeed)||targetSpeed<0||targetSpeed>1.8)throw new IllegalArgumentException("Invalid metered thrust");
    }
    public boolean virtual(){return source==PropulsionDemand.Source.VIRTUAL_PROPULSION;}
    public int windup(){return virtual()?0:RiptidePlanner.WINDUP;}
    public int maximumTicks(float strength){return virtual()?30:RiptidePlanner.maximumTicks(strength);}
    /** Bound added energy, not existing velocity: dive momentum survives and no braking teleport occurs. */
    public boolean regulated(){return virtual()&&targetSpeed>0;}
    public float force(double vx,double vy,double vz){
        if(!virtual())return acceleration;
        double speed=Math.sqrt(vx*vx+vy*vy+vz*vz);
        // Proportional speed hold plus bounded drag/climb feed-forward. Stateless feedback is
        // identical in the predictor and executor; no hidden integral windup after a safety cut.
        double requested=regulated()?Math.clamp(.025+.05*Math.max(0,vy)+.18*(targetSpeed-speed),0,acceleration):acceleration;
        return (float)Math.min(requested,Math.max(0,2.4-speed));
    }
}
