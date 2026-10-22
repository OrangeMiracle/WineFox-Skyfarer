package dev.winefox.skyfarer.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One server-thread ledger shared by all dimensions and maids; no threads or world objects. */
public final class Fox3Budget {
    public static final int MAX_ACTIVE = 128, MAX_PER_MAID = 16, LAUNCHES_PER_TICK = 16, SEARCHES_PER_TICK = 8;
    private final Map<UUID, Integer> active = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int launches, searches, total;
    private void advance(long now) {
        if (tick != now) { tick = now; launches = 0; searches = 0; }
    }
    public boolean search(long now) {
        advance(now);
        if (searches >= SEARCHES_PER_TICK) return false;
        searches++;
        return true;
    }
    public boolean available(UUID maid, long now) {
        advance(now);
        return launches < LAUNCHES_PER_TICK && total < MAX_ACTIVE && active.getOrDefault(maid, 0) < MAX_PER_MAID;
    }
    public boolean reserve(UUID maid, long now) {
        if (!available(maid, now)) return false;
        launches++; total++; active.merge(maid, 1, Integer::sum);
        return true;
    }
    public void release(UUID maid) {
        int count = active.getOrDefault(maid, 0);
        if (count == 0) return;
        if (count == 1) active.remove(maid); else active.put(maid, count - 1);
        total--;
    }
    public int activeCount() { return total; }
    public void clear() { active.clear(); total = launches = searches = 0; tick = Long.MIN_VALUE; }
}
