package dev.winefox.skyfarer.flight;

/** Pure numerical replay; does not load Minecraft classes, assets or a game world. */
public final class FlightMathChecks {
    public static void main(String[] args) {
        require(!FlightMath.passed(1, 1.5) && FlightMath.passed(-3, 1.5), "Release after crossing the pass, not before reaching the mob");
        require(!FlightMath.chargeDue(40, 1, 10) && FlightMath.chargeDue(12, 1, 10), "Respect charge windup near interception");
        System.out.println("Charge timing checks passed; Minecraft was not launched.");
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
