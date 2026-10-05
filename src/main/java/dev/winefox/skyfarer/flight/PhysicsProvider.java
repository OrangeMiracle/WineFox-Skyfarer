package dev.winefox.skyfarer.flight;

import java.util.Objects;
import java.util.function.Supplier;

/** Main-thread cache. Identity objects and the supplier NEVER enter a prediction DTO. */
public final class PhysicsProvider {
    private final Thread owner = Thread.currentThread();
    private Object entity, level, dimension;
    private long capturedAt = Long.MIN_VALUE;
    private PhysicsContext context = PhysicsContext.VANILLA;
    public PhysicsContext capture(Object entity, Object level, Object dimension, long tick,
                                  Supplier<PhysicsContext> read) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Physics capture off owner thread");
        // Once per real planning tick, never per simulated tick. Reentry/Level replacement
        // recaptures even when dimension key and game time happen to be identical.
        if (this.entity != entity || this.level != level || !Objects.equals(this.dimension, dimension) || capturedAt != tick) {
            this.entity = entity; this.level = level; this.dimension = dimension; capturedAt = tick;
            try { context = Objects.requireNonNullElse(read.get(), PhysicsContext.VANILLA); }
            catch (RuntimeException failure) { context = PhysicsContext.VANILLA; }
        }
        return context;
    }
    public void clear() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Physics release off owner thread");
        entity = level = dimension = null; capturedAt = Long.MIN_VALUE; context = PhysicsContext.VANILLA;
    }
}
