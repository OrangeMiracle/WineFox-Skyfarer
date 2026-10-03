package dev.winefox.skyfarer.ai;

import net.minecraft.world.phys.Vec3;
import java.util.List;

public record AirRoute(List<Waypoint> waypoints) {
    public record Waypoint(String name, Vec3 position) {}
    public AirRoute { waypoints = List.copyOf(waypoints); }
    public static final AirRoute EMPTY = new AirRoute(List.of());
}
