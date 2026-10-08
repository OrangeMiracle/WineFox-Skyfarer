package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/** Live server qualification shared by discovery, assignment and grounded order rechecks.
 * Saved registry records cannot know whether a maid is currently exposed to rain. */
public final class PropulsionAvailability {
    private PropulsionAvailability() {}
    public static String source(EntityMaid maid) {
        return PropulsionCapability.inspect(maid).source();
    }
    public static String rejection(EntityMaid maid) {
        if(!source(maid).equals("NONE"))return null;
        return maid.isInWaterOrRain()?"no_firework_or_usable_riptide":"no_firework_riptide_environment_invalid";
    }
}
