package dev.winefox.skyfarer.client.debug;

import dev.winefox.skyfarer.network.AirCombatDebugPayload;
import net.minecraft.nbt.CompoundTag;

public final class ClientDebugState {
    private static final java.util.Map<java.util.UUID,CompoundTag> snapshots=new java.util.LinkedHashMap<>();
    public static final dev.winefox.skyfarer.runtime.DebugSelection selection=new dev.winefox.skyfarer.runtime.DebugSelection();
    public static void accept(AirCombatDebugPayload payload) {
        var data=payload.data();
        if(data.hasUUID("remove")){var id=data.getUUID("remove");snapshots.remove(id);selection.remove(id);}
        else if(data.hasUUID("maid")){var id=data.getUUID("maid");snapshots.put(id,data);selection.add(id,data.getBoolean("select"));}
        else clear();
    }
    public static CompoundTag current(){return snapshots.getOrDefault(selection.selected(),new CompoundTag());}
    public static java.util.List<CompoundTag> all(){return java.util.List.copyOf(snapshots.values());}
    public static void clear(){snapshots.clear();selection.clear();}
}
