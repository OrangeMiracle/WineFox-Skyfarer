package dev.winefox.skyfarer.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only this class reads the backing config. AI and commands share the live values. */
public final class SkyfarerSettings {
    private SkyfarerSettings() {}
    private static final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
    public static long revision(){return revision.get();}
    public static void reloaded(){revision.incrementAndGet();}
    public static java.util.List<? extends String> extraElytraItems() {
        return SkyfarerConfig.SPEC.isLoaded() ? SkyfarerConfig.EXTRA_ELYTRA_ITEMS.get() : SkyfarerConfig.DEFAULT_ELYTRA_ITEMS;
    }
    public static double get(String name) {
        var p = SkyfarerConfig.PARAMETERS.get(name);
        if (p == null) throw new IllegalArgumentException("Unknown parameter: " + name);
        return SkyfarerConfig.SPEC.isLoaded() ? SkyfarerConfig.VALUES.get(name).get() : p.initial();
    }
    public static Map<String, Double> snapshot() {
        Map<String, Double> result = new LinkedHashMap<>();
        SkyfarerConfig.PARAMETERS.keySet().forEach(name -> result.put(name, get(name)));
        return result;
    }
    public static void set(String name, double value) {
        var p = SkyfarerConfig.PARAMETERS.get(name);
        if (p == null) throw new IllegalArgumentException("Unknown parameter: " + name);
        if (!Double.isFinite(value) || value < p.min() || value > p.max() || (p.integral() && value != Math.rint(value)))
            throw new IllegalArgumentException(name + " must be " + p.min() + ".." + p.max() + " " + p.unit() + (p.integral() ? " (integer)" : ""));
        var proposed = snapshot(); proposed.put(name, value); validateRanges(proposed);
        if (!SkyfarerConfig.SPEC.isLoaded()) throw new IllegalArgumentException("Server configuration is not loaded");
        SkyfarerConfig.VALUES.get(name).set(value);
        revision.incrementAndGet();
        SkyfarerConfig.SPEC.save();
    }
    private static void validateRanges(Map<String, Double> values) {
        if (values.get("combat_range") > values.get("hard_combat_range") || values.get("hard_combat_range") >= values.get("emergency_teleport_range"))
            throw new IllegalArgumentException("Require combat_range <= hard_combat_range < emergency_teleport_range; increase outer ranges first");
    }
    public static void reset(String name) {
        if (!SkyfarerConfig.SPEC.isLoaded()) throw new IllegalArgumentException("Server configuration is not loaded");
        if (name.equals("all")) {
            revision.incrementAndGet();
            SkyfarerConfig.PARAMETERS.forEach((key, p) -> SkyfarerConfig.VALUES.get(key).set(p.initial()));
            SkyfarerConfig.DEBUG_LOGGING.set(false);
            SkyfarerConfig.FOX3_ENABLED.set(true);
            SkyfarerConfig.FOX3_CONSUME_FIREWORKS.set(false);
            SkyfarerConfig.EXTRA_ELYTRA_ITEMS.set(SkyfarerConfig.DEFAULT_ELYTRA_ITEMS);
            SkyfarerConfig.SPEC.save();
        } else {
            var p = SkyfarerConfig.PARAMETERS.get(name);
            if (p == null) throw new IllegalArgumentException("Unknown parameter: " + name);
            set(name, p.initial());
        }
    }
    public static double maxTurnAngle() { return get("max_turn_angle"); }
    public static double turnRate() { return get("turn_rate"); }
    public static double attackTurnRate() { return get("attack_turn_rate"); }
    public static double diveMaxCorrectionAngle() { return get("dive_max_correction_angle"); }
    public static double pitchRate() { return get("pitch_rate"); }
    public static double pullUpPitchRate() { return get("pull_up_pitch_rate"); }
    public static double searchRange() { return get("search_range"); }
    public static double combatRange() { return Math.min(get("combat_range"), hardCombatRange()); }
    public static double hardCombatRange() { return Math.min(get("hard_combat_range"), emergencyTeleportRange() - 1); }
    public static double emergencyTeleportRange() { return get("emergency_teleport_range"); }
    public static double targetSwitchThreshold() { return get("target_switch_threshold"); }
    public static double attackEntryDistance() { return get("attack_entry_distance"); }
    public static double breakawayDistance() { return get("breakaway_distance"); }
    public static double attackExitDistance() { return breakawayDistance()*2/3; }
    public static int ticks(String name) { return (int) get(name); }
    public static boolean fox3Enabled() { return !SkyfarerConfig.SPEC.isLoaded() || SkyfarerConfig.FOX3_ENABLED.get(); }
    public static boolean fox3ConsumeFireworks() { return SkyfarerConfig.SPEC.isLoaded() && SkyfarerConfig.FOX3_CONSUME_FIREWORKS.get(); }
    public static int fox3MinMonsters() { return ticks("fox3_min_monsters"); }
    public static int fox3CooldownTicks() { return ticks("fox3_cooldown_ticks"); }
    public static double fox3Chance() { return get("fox3_chance"); }
    public static int fox3Waves() { return ticks("fox3_waves"); }
    public static int fox3WaveIntervalTicks() { return ticks("fox3_wave_interval_ticks"); }
    public static int fox3MissilesPerWave() { return ticks("fox3_missiles_per_wave"); }
    public static double fox3Damage() { return get("fox3_damage"); }
    public static double fox3BlastRadius() { return get("fox3_blast_radius"); }
    public static boolean debugLogging() { return SkyfarerConfig.SPEC.isLoaded() && SkyfarerConfig.DEBUG_LOGGING.get(); }
    public static void setDebugLogging(boolean enabled) {
        if (!SkyfarerConfig.SPEC.isLoaded()) throw new IllegalArgumentException("Server configuration is not loaded");
        SkyfarerConfig.DEBUG_LOGGING.set(enabled);
        SkyfarerConfig.SPEC.save();
    }
}
