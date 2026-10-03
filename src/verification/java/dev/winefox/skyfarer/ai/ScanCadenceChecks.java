package dev.winefox.skyfarer.ai;

/** Ordinary JVM regression check. Does not start Minecraft or create a game directory. */
public final class ScanCadenceChecks {
    public static void main(String[] args) {
        ScanCadence irregular = new ScanCadence();
        int scans = 0;
        for (int tick = 1; tick < 101; tick += 2) if (irregular.ready(tick, 10)) scans++;
        require(scans == 10, "Odd-tick Brain callbacks must still scan ten times");
        ScanCadence bothHooks = new ScanCadence();
        require(bothHooks.ready(42, 10), "First callback scans immediately");
        require(!bothHooks.ready(42, 10), "Entity and Brain hooks must not double scan");
        require(!bothHooks.ready(47, 10), "Respect current interval");
        require(bothHooks.ready(47, 5), "Shortened interval takes effect immediately");
        require(bothHooks.ready(0, 5), "Recover when a clock moves backward");
        System.out.println("Scan cadence checks passed; Minecraft was not launched.");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
