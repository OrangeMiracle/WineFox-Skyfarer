package dev.winefox.skyfarer.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Durable entitlement ledger. Keep entitlement after delivery: player/world saves are independent. */
public final class CometRewards extends SavedData {
    public static final ResourceLocation ADVANCEMENT=ResourceLocation.fromNamespaceAndPath("winefox_skyfarer","silver_wing_successor");
    private final Set<UUID> entitled=new HashSet<>();
    public static CometRewards get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CometRewards::new,CometRewards::load),"winefox_skyfarer_comet_rewards");}
    public void earned(MinecraftServer server,UUID owner){
        if(owner==null)return;
        remember(owner);
        var player=server.getPlayerList().getPlayer(owner);if(player!=null)deliver(player);
    }
    boolean remember(UUID owner){boolean added=entitled.add(owner);if(added)setDirty();return added;}
    boolean entitled(UUID owner){return entitled.contains(owner);}
    public void deliver(ServerPlayer player){
        if(!entitled.contains(player.getUUID()))return;
        var advancement=player.server.getAdvancements().get(ADVANCEMENT);
        if(advancement!=null&&!player.getAdvancements().getOrStartProgress(advancement).isDone())player.getAdvancements().award(advancement,"awakened");
    }
    static CometRewards load(CompoundTag tag,HolderLookup.Provider lookup){
        var result=new CometRewards();var list=tag.getList("owners",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++)if(list.getCompound(i).hasUUID("id"))result.entitled.add(list.getCompound(i).getUUID("id"));
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        var list=new ListTag();for(UUID id:entitled){var entry=new CompoundTag();entry.putUUID("id",id);list.add(entry);}tag.put("owners",list);return tag;
    }
}
