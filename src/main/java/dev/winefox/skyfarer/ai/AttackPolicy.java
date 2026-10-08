package dev.winefox.skyfarer.ai;

/** Pure combat decisions, shared with compile-only regression scenarios. */
public final class AttackPolicy {
    private AttackPolicy() {}
    public static boolean finishCycle(AirCombatState state) {
        return state.locksRunTarget() || state == AirCombatState.ATTACK_EXIT;
    }
    public static boolean canResume(String reason,boolean glide,boolean equipped,boolean fueled,boolean workAllowed,boolean timeLeft) {
        return glide && equipped && fueled && workAllowed && timeLeft && switch(reason) {
            case "NO_TARGET_AFTER_LOITER", "ALIGNMENT_TIMEOUT", "EXIT_PATH_TIMEOUT", "COMBAT_AREA_EMERGENCY_RANGE", "FLIGHT_LIMIT_OR_FUEL" -> true;
            default -> false;
        };
    }
    // Target pursuit range is not a leash on an aircraft making its turn outside that range.
    public static boolean emergency(double anchorDistance, double targetDistance, boolean hasTarget, double limit) {
        return anchorDistance > limit && (!hasTarget || targetDistance > limit);
    }
    public static double minimumEntry(double speed, int remainingWindup, double reach) {
        return Math.max(3, speed * (remainingWindup + 2) + reach);
    }
    public static boolean canEnter(double distance, double speed, int remainingWindup, double reach,
        double lookAngle, double velocityAngle, double attackTurnRate, double maxAngle) {
        double ticks = Math.max(0, (distance - reach) / Math.max(0.25, speed));
        double allowed = Math.min(18, Math.min(maxAngle, Math.max(4, (ticks - 2) * attackTurnRate)));
        return distance >= minimumEntry(speed, remainingWindup, reach)
            && lookAngle <= allowed && velocityAngle <= allowed;
    }
    public static double recoveryHeight(double currentEyeY, double targetEyeY, double approachHeight) {
        // Never add a fresh climb_height on top of the altitude already recovered in breakaway.
        return Math.max(currentEyeY, targetEyeY + approachHeight);
    }
    public static double exitLength(double breakaway,double speed,double turnRate) {
        double radius=Math.clamp(speed/Math.toRadians(turnRate),8,32);
        return Math.max(breakaway,radius*1.5)*2/3;
    }
    public static boolean exitComplete(int ticks,boolean reached,double travelled,double maximum,boolean safe) {
        return safe && (reached || travelled>=maximum || ticks>100);
    }
    public static double withdrawalEnvelope(int ticks, double speed, double turnRate, double breakaway) {
        if (ticks <= 0) return 0;
        double radius = Math.min(48, speed / Math.toRadians(turnRate));
        return ticks * speed + 2 * radius + breakaway + 8;
    }
}
