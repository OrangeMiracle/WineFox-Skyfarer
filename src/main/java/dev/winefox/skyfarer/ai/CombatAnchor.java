package dev.winefox.skyfarer.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.phys.Vec3;

/** Home has priority over following. Controllers snapshot this only between attack cycles. */
public final class CombatAnchor {
    private CombatAnchor() {}
    public static Vec3 current(EntityMaid maid) {
        Vec3 commanded=CommandAccess.anchor.apply(maid);if(commanded!=null)return commanded;
        if (maid.isHomeModeEnable()) return Vec3.atBottomCenterOf(maid.getRestrictCenter());
        var owner = maid.getOwner();
        return owner != null && owner.level() == maid.level() ? owner.position() : maid.position();
    }
}
