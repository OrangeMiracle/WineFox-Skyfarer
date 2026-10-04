package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Small persistent index, partitioned by owner. Unknown TLM records are verified after bounded wakeup. */
public final class SkyfarerMaidRegistry extends SavedData {
    public record Entry(UUID id,UUID owner,String dimension,BlockPos pos,boolean known,boolean home,boolean skyfarer) {}
    private final Map<UUID,Map<UUID,Entry>> owners=new HashMap<>();
    public static SkyfarerMaidRegistry get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(
        new Factory<>(SkyfarerMaidRegistry::new,SkyfarerMaidRegistry::load),"winefox_skyfarer_maids");}
    public List<Entry> owned(UUID owner){return new ArrayList<>(owners.getOrDefault(owner,Map.of()).values());}
    private void put(Entry e){
        for(var group:owners.entrySet())if(!group.getKey().equals(e.owner))group.getValue().remove(e.id);
        var old=owners.computeIfAbsent(e.owner,k->new HashMap<>()).put(e.id,e);
        if(!e.equals(old))setDirty();
    }
    public void observe(EntityMaid maid) {
        UUID owner=maid.getOwnerUUID(); if(owner==null){remove(maid.getUUID());return;}
        put(new Entry(maid.getUUID(),owner,maid.level().dimension().location().toString(),maid.blockPosition(),true,maid.isHomeModeEnable(),SkyfarerRuntime.isSkyfarer(maid)));
    }
    public void remove(UUID id){for(var group:owners.values())if(group.remove(id)!=null)setDirty();}
    public void seed(ServerPlayer player) {
        for(var info:MaidWorldData.get(player.level()).getPlayerMaidInfos(player))
            if(!owners.getOrDefault(player.getUUID(),Map.of()).containsKey(info.getEntityId()))
                put(new Entry(info.getEntityId(),player.getUUID(),info.getDimension(),info.getChunkPos(),false,false,false));
    }
    private static SkyfarerMaidRegistry load(CompoundTag tag,HolderLookup.Provider lookup) {
        var registry=new SkyfarerMaidRegistry();var entries=tag.getList("maids",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){var e=entries.getCompound(i);if(e.hasUUID("id")&&e.hasUUID("owner"))registry.put(new Entry(e.getUUID("id"),e.getUUID("owner"),e.getString("dim"),BlockPos.of(e.getLong("pos")),e.getBoolean("known"),e.getBoolean("home"),e.getBoolean("skyfarer")));}
        return registry;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        var entries=new ListTag();for(var group:owners.values())for(var e:group.values()){
            var t=new CompoundTag();t.putUUID("id",e.id);t.putUUID("owner",e.owner);t.putString("dim",e.dimension);t.putLong("pos",e.pos.asLong());t.putBoolean("known",e.known);t.putBoolean("home",e.home);t.putBoolean("skyfarer",e.skyfarer);entries.add(t);
        }tag.put("maids",entries);return tag;
    }
}
