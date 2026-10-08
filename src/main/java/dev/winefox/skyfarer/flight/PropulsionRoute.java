package dev.winefox.skyfarer.flight;

/** Immutable flight requirements, not item recipes. Only numerical data may cross the worker boundary. */
public sealed interface PropulsionRoute {
    record Turn(CompactTurnPlanner.Goal goal, double rate) implements PropulsionRoute {}
    record Curve() implements PropulsionRoute {}
    record Glide(GlidePlanner.Request request, double reserve, boolean aligned,
                 boolean allowExitChange, double exitLength) implements PropulsionRoute {}
}
