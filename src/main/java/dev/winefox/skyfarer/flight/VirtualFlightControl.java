package dev.winefox.skyfarer.flight;

/** One feedback candidate, not a search. Receding horizon execution commits only its next tick;
 * certification also proves the powered horizon and the existing unpowered recovery/strike exit. */
public final class VirtualFlightControl {
    private VirtualFlightControl() {}
    public static RiptidePlanner.Plan candidate(FlightPrediction.State start,PropulsionDemand demand){
        if(demand.departure()||demand.emergency()||demand.path()==null&&demand.aim()!=null)return null;
        double yaw=Math.toDegrees(Math.atan2(start.x()-demand.x(),demand.z()-start.z()));
        return new RiptidePlanner.Plan(yaw,0,demand.height(),demand.x(),demand.z(),0,
            demand.continuation()?1:12,false,Math.min(demand.minimumSpeed(),.9),0,java.util.List.of(),demand,ThrustProfile.cruise(demand.minimumSpeed()));
    }
}
