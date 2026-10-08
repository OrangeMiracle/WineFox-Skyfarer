package dev.winefox.skyfarer.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkyfarerConfig {
    public record Parameter(String category, String name, double initial, double min, double max, String unit, boolean integral) {}
    public static final Map<String, Parameter> PARAMETERS = new LinkedHashMap<>();
    static {
        define("flight", "max_turn_angle", 120, 10, 180, "degrees");
        define("flight", "turn_rate", 5, 0.1, 20, "degrees/tick");
        define("flight", "attack_turn_rate", 2.5, 0.1, 10, "degrees/tick");
        define("flight", "dive_max_correction_angle", 40, 5, 90, "degrees");
        define("flight", "pitch_rate", 3, 0.1, 15, "degrees/tick");
        define("flight", "pull_up_pitch_rate", 4, 0.1, 20, "degrees/tick");
        define("flight", "recovery_pitch_rate", 8, 1, 30, "degrees/tick");
        define("flight", "energy_reserve", 0.08, 0, 1, "specific energy");
        define("flight", "minimum_boost_speed", 0.8, 0.1, 3, "blocks/tick");
        define("flight", "attack_minimum_boost_speed", 0.4, 0.1, 2, "blocks/tick");
        define("flight", "attack_energy_reserve", 0.02, 0, 1, "specific energy");
        define("flight", "slope_pitch_rate", 6, 1, 20, "degrees/tick");
        define("flight", "slope_pull_up_pitch_rate", 10, 1, 30, "degrees/tick");
        define("flight", "firework_cooldown_ticks", 30, 10, 200, "ticks");
        define("flight", "takeoff_timeout_ticks", 60, 20, 200, "ticks");
        define("targeting", "search_range", 32, 4, 96, "blocks");
        define("targeting", "combat_range", 40, 8, 128, "blocks");
        define("targeting", "hard_combat_range", 56, 12, 192, "blocks");
        define("targeting", "target_switch_threshold", 15, 0, 100, "score");
        define("targeting", "scan_interval_ticks", 10, 1, 100, "ticks");
        define("combat", "attack_entry_distance", 18, 8, 48, "blocks");
        define("combat", "breakaway_distance", 12, 6, 40, "blocks");
        define("combat", "loiter_time_ticks", 80, 0, 600, "ticks");
        define("combat", "waypoint_tolerance", 3, 1, 8, "blocks");
        define("combat", "attack_timeout_ticks", 100, 20, 300, "ticks");
        define("combat", "max_target_displacement", 10, 2, 32, "blocks");
        define("combat", "approach_height", 4, 2, 12, "blocks");
        define("combat", "level_run_distance", 14, 6, 24, "blocks");
        define("combat", "reposition_distance", 28, 16, 48, "blocks");
        define("combat", "attack_ground_clearance", 0.75, 0.4, 2, "blocks");
        define("combat", "attack_aim_offset", 0.5, 0, 3, "blocks above target center");
        define("combat", "spear_vertical_expansion", 3, 0, 8, "blocks each side");
        define("combat", "max_failed_passes", 2, 1, 20, "attempts");
        define("combat", "post_attack_charge_ticks", 60, 0, 200, "ticks");
        define("combat", "secondary_target_radius", 6, 0, 12, "blocks");
        define("combat", "slope_sample_radius", 16, 3, 32, "blocks");
        define("combat", "slope_sample_step", 0.5, 0.25, 1, "blocks");
        define("safety", "emergency_teleport_range", 96, 32, 512, "blocks");
        define("safety", "corridor_side_margin", 0.35, 0.05, 1, "blocks");
        define("safety", "corridor_vertical_margin", 0.35, 0.05, 1, "blocks");
        define("safety", "return_timeout_ticks", 300, 60, 1200, "ticks");
        define("safety", "max_airborne_ticks", 2400, 200, 12000, "ticks");
        define("safety", "launch_retry_ticks", 60, 20, 400, "ticks");
        define("debug", "sync_interval_ticks", 5, 1, 40, "ticks");
        define("command", "command_mark_range", 256, 16, 512, "blocks");
        define("command", "command_home_range", 128, 16, 512, "blocks");
        define("command", "creative_command_range", 1000, 0, Double.MAX_VALUE, "blocks; 0 = unlimited");
        define("command", "creative_transit_speed_multiplier", 2, 1, 100, "multiplier");
        define("command", "creative_transit_height", 200, -64, 1024, "world Y; bounded by dimension ceiling");
        define("command", "command_mark_bonus", 150, 0, 1000, "score");
        define("flight", "prediction_length_multiplier", 1, 1, 10, "multiplier");
        define("debug", "log_interval_ticks", 100, 20, 1200, "ticks");
    }
    private static void define(String category, String name, double initial, double min, double max, String unit) {
        PARAMETERS.put(name, new Parameter(category, name, initial, min, max, unit, unit.equals("ticks") || unit.equals("attempts")));
    }
    public static final Map<String, ModConfigSpec.DoubleValue> VALUES = new LinkedHashMap<>();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> EXTRA_ELYTRA_ITEMS;
    public static final java.util.List<String> DEFAULT_ELYTRA_ITEMS=java.util.List.of("cataclysm:ignitium_elytra_chestplate");
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (Parameter p : PARAMETERS.values()) {
            builder.push(p.category());
            VALUES.put(p.name(), builder.comment("Unit: " + p.unit() + ". Live editable with /wfs config set.")
                .defineInRange(p.name(), p.initial(), p.min(), p.max()));
            builder.pop();
        }
        DEBUG_LOGGING = builder.push("debug")
            .comment("Diagnostic logging for target scans, blocked takeoff and state transitions. /wfs debuglog on|off|status. Disabled by default.")
            .define("debuglog", false);
        builder.pop();
        EXTRA_ELYTRA_ITEMS = builder.push("equipment")
            .comment("Additional Elytra-equivalent item IDs, e.g. cataclysm:ignitium_elytra_chestplate. Does not grant flight; item.canElytraFly must also allow it. Missing IDs are ignored.")
            .defineListAllowEmpty("extra_elytra_items", DEFAULT_ELYTRA_ITEMS, () -> "minecraft:elytra", value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }
    private SkyfarerConfig() {}
}
