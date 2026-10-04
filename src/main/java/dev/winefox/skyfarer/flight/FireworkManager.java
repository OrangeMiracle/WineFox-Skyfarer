package dev.winefox.skyfarer.flight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import dev.winefox.skyfarer.mixin.FireworkLifetimeAccessor;

public final class FireworkManager {
    private long lastBoost = -10000;
    private FireworkRocketEntity activeRocket;
    private String decision="not_requested";
    private int selectedDuration;
    public int selectedDuration(){return selectedDuration;}
    public String decision(){return decision;}
    public int cooldown(EntityMaid maid){return (int)Math.max(0,SkyfarerSettings.ticks("firework_cooldown_ticks")-(maid.level().getGameTime()-lastBoost));}
    public boolean boost(EntityMaid maid) {
        return boost(maid, 20);
    }
    public boolean boost(EntityMaid maid, int wantedTicks) {
        return boost(maid, duration -> RocketProfile.score(duration, wantedTicks, false));
    }
    /** Infinite scores reject a pulse whose complete lifetime has no safe recovery corridor. */
    public boolean boost(EntityMaid maid, java.util.function.IntToDoubleFunction pulseCost) {
        long now = maid.level().getGameTime();
        if(!maid.isFallFlying()){decision="not_gliding";return false;}
        if(active()){decision="active_pulse";return false;}
        if(cooldown(maid)>0){decision="cooldown_"+cooldown(maid);return false;}
        var inventory = maid.getAvailableInv(false);
        int selected = -1; double best = Double.POSITIVE_INFINITY;decision="no_fuel";
        var costs = new java.util.HashMap<Integer, Double>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.is(Items.FIREWORK_ROCKET) || inventory.extractItem(slot, 1, true).isEmpty()) continue;
            decision="pulse_trajectory_rejected";
            var data = stack.get(DataComponents.FIREWORKS);
            int duration = data == null ? 0 : data.flightDuration();
            double score = costs.computeIfAbsent(duration, pulseCost::applyAsDouble)
                + (data != null && !data.explosions().isEmpty() ? 1000 : 0);
            if (score < best) { best = score; selected = slot; }
        }
        if (selected >= 0) {
            var rocketStack = inventory.extractItem(selected, 1, true);
            if (rocketStack.isEmpty()) return false;
            // Add before committing extraction; every successful boost consumes exactly one real item.
            var rocket = new FireworkRocketEntity(maid.level(), rocketStack.copy(), maid);
            if (!maid.level().addFreshEntity(rocket)) return false;
            if (inventory.extractItem(selected, 1, false).isEmpty()) { rocket.discard(); return false; }
            activeRocket = rocket; lastBoost = now;decision="boosted";
            var profile=rocketStack.get(DataComponents.FIREWORKS);selectedDuration=profile==null?0:profile.flightDuration();return true;
        }
        return false;
    }
    public boolean active() { return activeRocket != null && activeRocket.isAlive() && !activeRocket.isRemoved(); }
    public int remainingBoostTicks() {
        if (!active()) return 0;
        var lifetime = (FireworkLifetimeAccessor)activeRocket;
        return Math.max(0, lifetime.wfs$lifetime() - lifetime.wfs$life() + 1);
    }
    public void detach() { if (active()) activeRocket.discard(); activeRocket = null; }
}
