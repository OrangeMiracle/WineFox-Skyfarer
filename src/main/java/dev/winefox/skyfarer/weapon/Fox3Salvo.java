package dev.winefox.skyfarer.weapon;

/** Pure, bounded wave clock. Delayed ticks never produce a catch-up burst. */
public final class Fox3Salvo {
    private final int interval;
    private final long deadline;
    private int remaining;
    private long next;

    public Fox3Salvo(int waves, int interval, long now) {
        if (waves < 1 || waves > 16 || interval < 1 || interval > 100) throw new IllegalArgumentException("Fox3 salvo bounds");
        this.remaining = waves;
        this.interval = interval;
        this.next = now;
        this.deadline = now + (long) waves * interval + 200;
    }
    public boolean active(long now) { return remaining > 0 && now <= deadline; }
    public boolean due(long now) { return active(now) && now >= next; }
    public void fired(long now) {
        if (!due(now)) throw new IllegalStateException("Fox3 wave is not due");
        remaining--;
        next = now + interval;
    }
    public static boolean automaticDue(long now, long last, int cooldown) {
        return now >= last && now - last >= cooldown;
    }
}
