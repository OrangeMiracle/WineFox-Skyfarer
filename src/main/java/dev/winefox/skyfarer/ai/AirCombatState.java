package dev.winefox.skyfarer.ai;

public enum AirCombatState {
    GROUNDED, TARGET_ACQUIRE, TARGET_SELECTED, TAKEOFF_PREP, TAKEOFF, WATER_ESCAPE, CLIMB,
    APPROACH, REPOSITION, ATTACK_RUN, DIVE, SPEAR_CHARGE, BREAKAWAY, ATTACK_EXIT,
    LOITER_SEARCH, RETURN_TO_OWNER, LANDING, ABORT, EMERGENCY;

    public boolean locksRunTarget() {
        return this == ATTACK_RUN || this == DIVE || this == SPEAR_CHARGE || this == BREAKAWAY;
    }
    public boolean controlsAir() { return this != GROUNDED && this != TARGET_ACQUIRE && this != TARGET_SELECTED; }
}
