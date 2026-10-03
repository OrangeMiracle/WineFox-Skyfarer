package dev.winefox.skyfarer.ai;

/** Elapsed-time gate: irregular Brain callback phases must never starve a scan. */
public final class ScanCadence {
    private long last = Long.MIN_VALUE;
    public boolean ready(long now, int interval) {
        if (last != Long.MIN_VALUE && now >= last && now - last < interval) return false;
        last = now;
        return true;
    }
}
