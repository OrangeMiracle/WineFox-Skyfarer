package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import java.util.List;
import java.util.HashMap;

/** Owner-thread firework adapter: inventory, lifetime, cooldown and full-life certification stay here. */
final class FireworkProvider {
    final FireworkManager motor;
    private final boolean transit;
    private List<Integer> candidates=List.of();
    private long candidateTick=Long.MIN_VALUE;
    FireworkProvider(boolean transit){this.transit=transit;motor=new FireworkManager(transit);}
    private final HashMap<Integer, GlidePlanner.Request> alternatives = new HashMap<>();
    private String review = "not_requested";
    void begin(EntityMaid maid){candidates=transit?List.of(0):FireworkManager.durations(maid);candidateTick=maid.level().getGameTime();}
    List<Integer> durations(EntityMaid maid) {
        if(!PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK))return List.of();
        if(transit)return List.of(0);
        if(candidateTick!=maid.level().getGameTime())begin(maid);
        return candidates; // Speculative only. Execution always checks/extracts the live stack again.
    }
    List<Integer> attackDurations(EntityMaid maid) {
        return motor.active() || motor.cooldown(maid)>0 ? List.of() : durations(maid).stream()
            .filter(d -> RocketProfile.maximumTicks(d)<=80).toList();
    }
    void resetReview(){ alternatives.clear(); review="not_requested"; }
    String review(){return review;}
    GlidePlanner.Request selectedRequest(){return alternatives.get(motor.selectedDuration());}
    double certify(FlightPrediction.State start, PropulsionDemand demand, PropulsionRoute route,
                   AsyncPlanning planning, GlidePlanner.Corridor space, int duration) {
        double gravity=planning.physics().gravity();
        if(route instanceof PropulsionRoute.Turn turn)
            return planning.compactPulse(start,turn.goal(),duration,gravity,turn.rate(),space);
        if(route instanceof PropulsionRoute.Curve)
            return planning.sweepPulse(start,demand.path(),demand.aim(),duration,gravity,demand.rate(),demand.minimumSpeed(),demand.exiting(),space);
        var glide=(PropulsionRoute.Glide)route;
        if(!glide.aligned())return Double.POSITIVE_INFINITY;
        var original=glide.request();
        var checked=planning.reviewPulse(start,original,space,duration,demand.minimumSpeed(),glide.reserve());
        review=checked.reason();
        if(!Double.isFinite(checked.cost())&&glide.allowExitChange()&&original.aim()!=null&&original.exit()!=null){
            for(double turn:new double[]{0,-45,45}){
                double angle=Math.toRadians(original.yaw()+turn);var aim=original.aim();
                var exit=new GlidePlanner.Exit(aim.x()-Math.sin(angle)*glide.exitLength(),original.height()+(turn==0?6:4),aim.z()+Math.cos(angle)*glide.exitLength(),0,0);
                var alternative=original.withExit(exit);
                // The complete contact/exit rollout checks the body corridor; no live Entity/Level callback.
                var proof=planning.reviewPulse(start,alternative,space,duration,demand.minimumSpeed(),glide.reserve());
                if(Double.isFinite(proof.cost())){alternatives.put(duration,alternative);review="replanned_exit";return proof.cost()+1;}
            }
        }
        return checked.cost();
    }
    void warm(EntityMaid maid,FlightPrediction.State start,PropulsionDemand demand,PropulsionRoute route,
              AsyncPlanning planning,GlidePlanner.Corridor space){
        if(!motor.active())for(int duration:durations(maid))certify(start,demand,route,planning,space,duration);
    }
}
