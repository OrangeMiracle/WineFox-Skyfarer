package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.AirCombatState;
import dev.winefox.skyfarer.ai.AirRoute;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DebugSnapshot {
    public static void corridor(CompoundTag data,EntityMaid maid,java.util.List<dev.winefox.skyfarer.flight.GlidePlanner.TracePoint> path) {
        var list=new ListTag();
        for(var point:path) {var t=new CompoundTag();var s=point.state();vector(t,"center",new Vec3(s.x(),s.y(),s.z()));
            t.putDouble("radius",maid.getBbWidth()*.5+point.side());t.putDouble("below",maid.getEyeHeight()+point.vertical());
            t.putDouble("above",maid.getBbHeight()-maid.getEyeHeight()+point.vertical());list.add(t);}
        data.put("corridor",list);
    }
    public static CompoundTag make(EntityMaid maid, AirCombatState state, LivingEntity preferred, LivingEntity run,
        java.util.UUID previous, double score, Vec3 desired, double angle, AirRoute route) {
        CompoundTag data = new CompoundTag();
        data.putUUID("maid", maid.getUUID()); data.putInt("entity_id", maid.getId());
        data.putString("maid_name", maid.getName().getString());
        data.putString("dimension", maid.level().dimension().location().toString());
        data.putString("state", state.name()); data.putLong("time", maid.level().getGameTime());
        vector(data, "position", maid.position()); vector(data, "velocity", maid.getDeltaMovement());
        vector(data, "heading", maid.getLookAngle()); vector(data, "desired", desired);
        data.putDouble("angle", angle); data.putDouble("score", score);
        if (preferred != null) { data.putUUID("preferred", preferred.getUUID()); data.putString("preferred_name", preferred.getName().getString()); vector(data, "preferred_position", preferred.getBoundingBox().getCenter()); }
        if (run != null && run.isAlive()) { data.putUUID("run", run.getUUID()); data.putString("target_name", run.getName().getString()); vector(data, "target", run.getBoundingBox().getCenter()); }
        if (previous != null) data.putUUID("previous", previous);
        data.putDouble("previous_bonus", 0); // Previous target is now a strategic tier, not an additive route bonus.
        data.putDouble("max_turn", SkyfarerSettings.maxTurnAngle()); data.putDouble("turn_rate", state == AirCombatState.DIVE || state == AirCombatState.SPEAR_CHARGE ? SkyfarerSettings.attackTurnRate() : SkyfarerSettings.turnRate());
        data.putDouble("search_range", SkyfarerSettings.searchRange()); data.putDouble("combat_range", dev.winefox.skyfarer.ai.TargetEvaluator.combatRange(maid));
        ListTag waypoints = new ListTag();
        for (var waypoint : route.waypoints()) { CompoundTag point = new CompoundTag(); point.putString("name", waypoint.name()); vector(point, "pos", waypoint.position()); waypoints.add(point); }
        data.put("route", waypoints); return data;
    }
    public static void vector(CompoundTag tag, String key, Vec3 vector) {
        CompoundTag v = new CompoundTag(); v.putDouble("x", vector.x); v.putDouble("y", vector.y); v.putDouble("z", vector.z); tag.put(key, v);
    }
    public static Vec3 vector(CompoundTag tag, String key) { var v = tag.getCompound(key); return new Vec3(v.getDouble("x"), v.getDouble("y"), v.getDouble("z")); }
}
