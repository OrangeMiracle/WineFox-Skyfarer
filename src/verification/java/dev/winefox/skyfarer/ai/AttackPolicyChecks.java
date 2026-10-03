package dev.winefox.skyfarer.ai;

/** Regression decisions from the user's two completed passes followed by OWNER_HARD_RANGE. */
public final class AttackPolicyChecks {
    public static void main(String[] args) {
        for (AirCombatState state : new AirCombatState[]{AirCombatState.ATTACK_RUN, AirCombatState.DIVE,
            AirCombatState.SPEAR_CHARGE, AirCombatState.BREAKAWAY, AirCombatState.ATTACK_EXIT})
            require(AttackPolicy.finishCycle(state), "Ordinary return must wait through " + state);
        require(!AttackPolicy.finishCycle(AirCombatState.APPROACH), "Do not start a new run after fuel/time limit");
        require(!AttackPolicy.emergency(80, 35, true, 96), "Pull-up outside old 56-block leash must continue");
        require(!AttackPolicy.emergency(112, 60, true, 96), "Allow a turn outside the target acquisition sphere");
        require(AttackPolicy.emergency(160, 110, true, 96), "A runaway flight must still return");
        require(AttackPolicy.emergency(100, 0, false, 96), "No target cannot justify unlimited flight");
        double withdrawal = AttackPolicy.withdrawalEnvelope(60, 1.7, 5, 12);
        require(withdrawal > 142 && withdrawal < 170, "Reserve a 3-second rocket-powered withdrawal plus the return turn");
        require(!AttackPolicy.emergency(160, 140, true, withdrawal), "Planned charge withdrawal must not force owner return");
        require(AttackPolicy.withdrawalEnvelope(0, 1.7, 5, 12) == 0, "Disabled charge tail adds no range allowance");
        require(AttackPolicy.canEnter(15, 0.8, 10, 2.25, 10, 12, 2.5, 120), "Usable pass below old 18-block/8-degree gate");
        require(!AttackPolicy.canEnter(10, 1.7, 10, 2.25, 0, 0, 2.5, 120), "Do not pretend an uncharged high-speed pass can hit");
        require(!AttackPolicy.canEnter(30, 1, 10, 2.25, 0, 50, 2.5, 120), "Facing target while velocity points away is not an entry");
        require(!AttackPolicy.canEnter(15, 0.8, 10, 2.25, 12, 12, 0.1, 120), "Respect configured low turn rate");
        double height = 5.3;
        for (int pass = 0; pass < 100; pass++) height = AttackPolicy.recoveryHeight(height, 1.3, 4);
        require(Math.abs(height - 5.3) < 1e-6, "Recovery height must not accumulate on repeated passes");
        System.out.println("Attack cycle, maneuver boundary, entry opportunity and repeated recovery checks passed.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
