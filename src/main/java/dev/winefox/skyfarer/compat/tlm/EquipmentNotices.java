package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Main-thread UI adapter. Episode/cooldown survive controller replacement and entity save/reload. */
public final class EquipmentNotices {
    private static final String KEY="wfs_equipment_notices";
    private static final Map<Object,Map<UUID,Long>> OWNER_TIMES=new WeakHashMap<>();
    private EquipmentNotices() {}
    public static void clear(){OWNER_TIMES.clear();}
    public static void tick(EntityMaid maid,boolean ready) {
        tick(maid,ready,maid.onGround());
    }
    public static void tick(EntityMaid maid,boolean ready,boolean groundMovement) {
        var persistent=maid.getPersistentData();
        if(ready&&!persistent.contains(KEY))return;
        var tag=persistent.getCompound(KEY);var state=new EquipmentNoticePolicy();
        state.seen=tag.getInt("seen");state.pending=tag.getInt("pending");
        state.normalSince=tag.contains("normalSince")?tag.getLong("normalSince"):-1;
        state.lastSpoken=tag.contains("lastSpoken")?tag.getLong("lastSpoken"):-EquipmentNoticePolicy.COOLDOWN;
        state.due=tag.getLong("due");
        long now=maid.level().getGameTime();
        state.observe(now,ready,groundMovement,1+Math.floorMod(maid.getUUID().hashCode(),40));
        int event=state.readyEvent(now);
        if(event!=0&&maid.getOwnerUUID()!=null) {
            var times=OWNER_TIMES.computeIfAbsent(maid.level(),unused->new HashMap<>());
            times.entrySet().removeIf(e->now<e.getValue()||now-e.getValue()>=EquipmentNoticePolicy.OWNER_INTERVAL);
            if(!times.containsKey(maid.getOwnerUUID())) {
                SkyfarerEquipment.report(maid,EquipmentNoticePolicy.name(event));state.spoken(now);times.put(maid.getOwnerUUID(),now);
            }
        }
        tag.putInt("seen",state.seen);tag.putInt("pending",state.pending);tag.putLong("normalSince",state.normalSince);
        tag.putLong("lastSpoken",state.lastSpoken);tag.putLong("due",state.due);persistent.put(KEY,tag);
    }
}
