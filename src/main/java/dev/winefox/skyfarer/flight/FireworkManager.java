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
    /** EntityTickList iterates a stable map: an entity added during the maid tick first ticks next server tick. */
    public int ignitionDelay(EntityMaid maid){return active()&&lastBoost==maid.level().getGameTime()?1:0;}
    public int remainingBoostTicks() {
        if (!active()) return 0;
        var lifetime = (FireworkLifetimeAccessor)activeRocket;
        return Math.max(0, lifetime.wfs$lifetime() - lifetime.wfs$life() + 1);
    }
    public void detach() { if (active()) activeRocket.discard(); activeRocket = null; }
}
