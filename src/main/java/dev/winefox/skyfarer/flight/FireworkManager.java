package dev.winefox.skyfarer.flight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import dev.winefox.skyfarer.mixin.FireworkLifetimeAccessor;

public final class FireworkManager {
    private final boolean transit;
    public FireworkManager(){this(false);}
    /** Preserves the pre-existing dispatch transport contract; never enabled for combat. */
    public FireworkManager(boolean transit){this.transit=transit;}
    public static java.util.List<Integer> durations(EntityMaid maid){
        if(!dev.winefox.skyfarer.compat.tlm.PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK))return java.util.List.of();
        var durations=new java.util.TreeSet<Integer>();var inventory=maid.getAvailableInv(false);
        for(int slot=0;slot<inventory.getSlots();slot++){
            var stack=inventory.getStackInSlot(slot);
            if(!stack.is(Items.FIREWORK_ROCKET)||inventory.extractItem(slot,1,true).isEmpty())continue;
            var data=stack.get(DataComponents.FIREWORKS);durations.add(data==null?0:data.flightDuration());
        }
        return durations.stream().limit(3).toList();
    }
    private long lastBoost = -10000;
    private FireworkRocketEntity activeRocket;
    private String decision="not_requested";
    private int selectedDuration;
    public int selectedDuration(){return selectedDuration;}
    public String decision(){return decision;}
    public int cooldown(EntityMaid maid){return transit?0:(int)Math.max(0,SkyfarerSettings.ticks("firework_cooldown_ticks")-(maid.level().getGameTime()-lastBoost));}
    /** Infinite scores reject a pulse whose complete lifetime has no safe recovery corridor. */
    public boolean boost(EntityMaid maid, java.util.function.IntToDoubleFunction pulseCost) {
        if(!dev.winefox.skyfarer.compat.tlm.PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK)){decision="SOURCE_POLICY_DENIED";return false;}
        long now = maid.level().getGameTime();
        if(!maid.isFallFlying()){decision="not_gliding";return false;}
        if(active()){decision="active_pulse";return false;}
        if(cooldown(maid)>0){decision="cooldown_"+cooldown(maid);return false;}
        if(transit){
            if(!Double.isFinite(pulseCost.applyAsDouble(0))){decision="pulse_trajectory_rejected";return false;}
            var stack=new net.minecraft.world.item.ItemStack(Items.FIREWORK_ROCKET);
            stack.set(DataComponents.FIREWORKS,new net.minecraft.world.item.component.Fireworks(0,java.util.List.of()));
            var rocket=new FireworkRocketEntity(maid.level(),stack,maid);
            if(!maid.level().addFreshEntity(rocket)){decision="spawn_rejected";return false;}
            if(!dev.winefox.skyfarer.compat.tlm.PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK)){rocket.discard();decision="SOURCE_CHANGED_DURING_SPAWN";return false;}
            activeRocket=rocket;lastBoost=now;selectedDuration=0;decision="transit_boosted";return true;
        }
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
            // Resolve spawn cancellation and successful extraction BEFORE the saving lottery.
            var rocket = new FireworkRocketEntity(maid.level(), rocketStack.copy(), maid);
            if (!maid.level().addFreshEntity(rocket)) return false;
            if(!dev.winefox.skyfarer.compat.tlm.PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK)){rocket.discard();decision="SOURCE_CHANGED_DURING_SPAWN";return false;}
            if(!net.minecraft.world.item.ItemStack.isSameItemSameComponents(rocketStack,inventory.extractItem(selected,1,true))){rocket.discard();decision="fuel_changed_during_spawn";return false;}
            var consumed=inventory.extractItem(selected, 1, false);
            if (consumed.isEmpty()) { rocket.discard(); return false; }
            if(dev.winefox.skyfarer.compat.tlm.CometBaubles.saving(maid)
                &&maid.getRandom().nextDouble()<SkyfarerSettings.get("comet_firework_saving_chance")) {
                // CombinedInvWrapper is modifiable. No callbacks occur between extraction and
                // restoring this one item; preserve any remainder and its stack components.
                var remainder=inventory.getStackInSlot(selected);
                var restored=consumed.copy();restored.setCount(remainder.getCount()+consumed.getCount());
                inventory.setStackInSlot(selected,restored);
            }
            activeRocket = rocket; lastBoost = now;decision="boosted";
            var profile=rocketStack.get(DataComponents.FIREWORKS);selectedDuration=profile==null?0:profile.flightDuration();return true;
        }
        return false;
    }
    public boolean active() { return activeRocket != null && activeRocket.isAlive() && !activeRocket.isRemoved(); }
    /** EntityTickList iterates a stable map: an entity added during the maid tick first ticks next server tick. */
    public int ignitionDelay(EntityMaid maid){return active()&&lastBoost==maid.level().getGameTime()?1:0;}
    public int remainingBoostTicks() {
        if (!active()) return 0;
        var lifetime = (FireworkLifetimeAccessor)activeRocket;
        return Math.max(0, lifetime.wfs$lifetime() - lifetime.wfs$life() + 1);
    }
    public void detach() { if (active()) activeRocket.discard(); activeRocket = null; }
}
