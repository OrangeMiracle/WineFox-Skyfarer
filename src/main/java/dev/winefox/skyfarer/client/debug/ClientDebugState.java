package dev.winefox.skyfarer.client.debug;

import dev.winefox.skyfarer.network.AirCombatDebugPayload;
import net.minecraft.nbt.CompoundTag;

public final class ClientDebugState {
    private static CompoundTag current = new CompoundTag();
    private static long received;
    public static void accept(AirCombatDebugPayload payload) { current = payload.data(); received = System.nanoTime(); }
    public static CompoundTag current() { return System.nanoTime() - received < 5_000_000_000L ? current : new CompoundTag(); }
    public static void clear() { current = new CompoundTag(); }
}
