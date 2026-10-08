package dev.winefox.skyfarer.flight;

import java.util.List;
import java.util.Objects;

/** A selected feedback path outlives planner jobs. Its finite proof does not.
 * Proof time is the simulation start, never the mailbox arrival or the last read. */
public final class TrajectoryCommitment {
    public static final int MAX_PROOF_AGE = 6;
    public record Proof(long tick, SweepingAttackPlanner.Path path, FlightPrediction.Aim aim,
                        int rocket, double gravity, double rate, double minimum, boolean exiting,
                        SweepingAttackPlanner.Rollout rollout) {
        public boolean matches(long now, FlightPrediction.State state, SweepingAttackPlanner.Path wanted,
                               FlightPrediction.Aim target, int active, double g, double turn, double speed, boolean exit) {
            long age = now - tick;
            if (path != wanted || exiting != exit || g != gravity || turn != rate || speed != minimum
                || age < 0 || age > MAX_PROOF_AGE || age >= rollout.trace().size()) return false;
            var initial = rollout.trace().getFirst().state();
            if (active != RocketProfile.remaining((int) age, rocket, initial)) return false;
            var expected = rollout.trace().get((int) age).state();
            if (!stateMatches(state, expected) || !aimMatches(target, aim == null ? null : aim.after((int) age))) return false;
            // Predicted contact never substitutes for native damage. Once that instant passes,
            // the still-attacking controller must prove a new contact from the actual state.
            return exiting || !rollout.hit() || age == 0 || age < rollout.contactTick();
        }
        public SweepingAttackPlanner.Rollout remaining(long now) {
            int age = (int) Math.clamp(now - tick, 0, Math.max(0, rollout.trace().size() - 1));
            List<GlidePlanner.TracePoint> suffix = rollout.trace().subList(age, rollout.trace().size());
            double cost = rollout.cost() * (suffix.size() / (double) Math.max(1, rollout.trace().size()));
            return new SweepingAttackPlanner.Rollout(rollout.safe(), rollout.hit(),
                Math.max(-1, rollout.contactTick() - age), cost, rollout.miss(), rollout.first(), suffix, rollout.envelope());
        }
    }
    public static boolean stateMatches(FlightPrediction.State actual, FlightPrediction.State expected) {
        return Math.hypot(Math.hypot(actual.x()-expected.x(), actual.z()-expected.z()), actual.y()-expected.y()) <= .65
            && Math.abs(actual.vx()-expected.vx()) + Math.abs(actual.vy()-expected.vy()) + Math.abs(actual.vz()-expected.vz()) <= .25
            && Math.abs(FlightPrediction.turn(actual.yaw(), expected.yaw(), 180)-actual.yaw()) <= 6
            && Math.abs(actual.pitch()-expected.pitch()) <= 6
            && Objects.equals(actual.physics(), expected.physics()) && actual.rocketDelay() == expected.rocketDelay();
    }
    private static boolean aimMatches(FlightPrediction.Aim a, FlightPrediction.Aim b) {
        return a == b || a != null && b != null
            && Math.hypot(Math.hypot(a.x()-b.x(), a.z()-b.z()), a.bottom()-b.bottom()) <= .6
            && Math.abs(a.vx()-b.vx()) + Math.abs(a.vy()-b.vy()) + Math.abs(a.vz()-b.vz()) <= .12
            && a.height()==b.height() && a.halfWidth()==b.halfWidth() && a.minReach()==b.minReach()
            && a.maxReach()==b.maxReach() && a.margin()==b.margin() && a.readyIn()==b.readyIn();
    }

    private SweepingAttackPlanner.Path path;
    private Proof proof;
    private long id, selectedAt;
    private int switches;
    private String reason = "NONE";
    public void select(SweepingAttackPlanner.Path chosen, long now, String why) {
        path = chosen; proof = null; selectedAt = now; id++; reason = why;
    }
    public void clear(String why) { path = null; proof = null; reason = why; }
    public void phaseChanged() { phaseChanged("EXIT_REVALIDATION"); }
    public void phaseChanged(String why) { proof = null; if(path!=null)reason = why; }
    public void accept(Proof update) {
        if (update != null && update.path() == path && (proof == null || update.tick() >= proof.tick())) {
            proof = update; reason = update.rollout().safe() ? "CERTIFIED" : "PHYSICS_REJECTED";
        }
    }
    public Proof usable(long now, FlightPrediction.State state, FlightPrediction.Aim aim, int rocket,
                        double gravity, double rate, double minimum, boolean exiting) {
        return proof != null && proof.matches(now, state, path, aim, rocket, gravity, rate, minimum, exiting) ? proof : null;
    }
    /** Compare current-state full rollouts, not old total costs. Keep the current native use session. */
    public boolean better(long now, SweepingAttackPlanner.Rollout incumbent, SweepingAttackPlanner.Rollout candidate) {
        if (now-selectedAt < 12 || !candidate.safe() || !candidate.hit() || !incumbent.safe() || !incumbent.hit()
            || incumbent.contactTick() <= 12) return false;
        double yaw = Math.abs(FlightPrediction.turn(incumbent.first().yaw(), candidate.first().yaw(), 180)-incumbent.first().yaw());
        double pitch = Math.abs(candidate.first().pitch()-incumbent.first().pitch());
        double switching = Math.max(6, incumbent.cost()*.2) + yaw*.08 + pitch*.05;
        return candidate.cost() + switching < incumbent.cost();
    }
    public void switched(SweepingAttackPlanner.Path chosen, long now) { switches++; select(chosen, now, "BETTER_ROUTE"); }
    public long id() { return id; }
    public int switches() { return switches; }
    public long proofAge(long now) { return proof==null?-1:now-proof.tick(); }
    public String detail(long now) {
        return "id="+id+" active="+(path!=null)+" proofAge="+proofAge(now)+" switches="+switches+" reason="+reason;
    }
}
