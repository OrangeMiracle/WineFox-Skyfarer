package dev.winefox.skyfarer.ai;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Actual damage outcomes, not proximity or a completed flight, determine success. */
public final class SpearPassHistory {
    private final Map<UUID, Integer> misses = new LinkedHashMap<>();
    private final Set<UUID> hits = new HashSet<>();
    private UUID primary;
    public void begin(UUID target) { finish(); primary = target; hits.clear(); }
    public boolean alreadyHit(UUID target) { return primary != null && hits.contains(target); }
    public boolean hit(UUID target) {
        if (primary == null || !hits.add(target)) return false;
        misses.remove(target);
        return true;
    }
    public boolean primaryHit() { return primary != null && hits.contains(primary); }
    public void finish() {
        if (primary == null) return;
        if (!hits.contains(primary)) misses.merge(primary, 1, (a, b) -> Math.min(1000, a + b));
        else misses.remove(primary);
        if (misses.size() > 256) misses.remove(misses.keySet().iterator().next());
        primary = null;
        hits.clear();
    }
    public Set<UUID> avoided(int limit) {
        Set<UUID> result = new HashSet<>();
        misses.forEach((id, count) -> { if (count >= limit) result.add(id); });
        return result;
    }
}
