package dev.winefox.skyfarer.compat.spears;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ordinary left-click geometry; the inner blind zone is never enlarged or removed. */
public final class GroundMeleeReach {
    private GroundMeleeReach() {}
    public static double maximum(double itemMaximum,double interactionRange,int favorabilityBonus) {
        // TLM's default interaction range is 2.0. Keep Spears' native base and add TLM bonuses.
        return itemMaximum+Math.max(0,interactionRange-2.0)+Math.max(0,favorabilityBonus);
    }
    public static double stoppingMaximum(double maximum){return maximum-.2;}
    public static double spacing(double minimum,double maximum){return minimum+(maximum-minimum)*.65;}
    public static boolean contact(Vec3 eye,Vec3 direction,AABB target,double minimum,double maximum) {
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<0||maximum<=minimum)return false;
        var from=eye.add(direction.scale(minimum));
        return target.contains(from)||target.clip(from,eye.add(direction.scale(maximum))).isPresent();
    }
}
