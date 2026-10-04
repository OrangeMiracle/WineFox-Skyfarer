package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.command.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.network.AirCombatDebugPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class DebugWatchManager {
    private static final Map<UUID,LinkedHashMap<UUID,CompoundTag>> WATCHES=new HashMap<>();
    public static boolean allowed(ServerPlayer player,UUID owner){return owner!=null&&(owner.equals(player.getUUID())||player.hasPermissions(2));}
    public static boolean bound(ServerPlayer player,UUID maid){return WATCHES.getOrDefault(player.getUUID(),new LinkedHashMap<>()).containsKey(maid);}
    public static List<SkyfarerMaidRegistry.Entry> cachedEntries(ServerPlayer player){
        var entries=new ArrayList<SkyfarerMaidRegistry.Entry>();
        for(var data:WATCHES.getOrDefault(player.getUUID(),new LinkedHashMap<>()).values())entries.add(new SkyfarerMaidRegistry.Entry(data.getUUID("maid"),data.getUUID("owner"),data.getString("dimension"),net.minecraft.core.BlockPos.containing(DebugSnapshot.vector(data,"position")),false,false,false,data.getString("maid_name")));
        return entries;
    }
    public static EntityMaid loaded(MinecraftServer server,UUID id){
        for(var level:server.getAllLevels())if(level.getEntity(id) instanceof EntityMaid maid&&!maid.isRemoved())return maid;
        return null;
    }
    public static void toggle(ServerPlayer player,EntityMaid maid){
        var registry=SkyfarerMaidRegistry.get(player.server);registry.observe(maid);
        var entry=registry.find(maid.getUUID());if(entry==null)return;
        boolean enable=!bound(player,maid.getUUID());set(player,entry,enable);
        player.displayClientMessage(Component.translatable("message.winefox_skyfarer.debug_"+(enable?"on":"off"),maid.getName()),true);
    }
    public static void set(ServerPlayer player,SkyfarerMaidRegistry.Entry entry,boolean enable){
        if(!allowed(player,entry.owner()))return;
        if(!enable){remove(player,entry.id());return;}
        var watches=WATCHES.computeIfAbsent(player.getUUID(),k->new LinkedHashMap<>());
        var data=new CompoundTag();data.putUUID("maid",entry.id());data.putUUID("owner",entry.owner());
        data.putString("maid_name",entry.name());data.putString("dimension",entry.dimension());
        DebugSnapshot.vector(data,"position",Vec3.atBottomCenterOf(entry.pos()));
        data.putString("state","尚无实时数据");data.putBoolean("unloaded",true);
        watches.putIfAbsent(entry.id(),data);update(player,entry.id(),true);
    }
    private static void remove(ServerPlayer player,UUID id){
        var watches=WATCHES.get(player.getUUID());if(watches!=null)watches.remove(id);
        var data=new CompoundTag();data.putUUID("remove",id);PacketDistributor.sendToPlayer(player,new AirCombatDebugPayload(data));
    }
    public static void clear(ServerPlayer player){WATCHES.remove(player.getUUID());PacketDistributor.sendToPlayer(player,new AirCombatDebugPayload(new CompoundTag()));}
    public static void logout(UUID player){WATCHES.remove(player);DebugPanelManager.logout(player);}
    public static void clearAll(){WATCHES.clear();DebugPanelManager.clear();}
    public static void tick(ServerTickEvent.Post event){
        DebugPanelManager.tick(event.getServer());
        if(event.getServer().overworld().getGameTime()%SkyfarerSettings.ticks("sync_interval_ticks")!=0)return;
        for(var id:new ArrayList<>(WATCHES.keySet())){
            var player=event.getServer().getPlayerList().getPlayer(id);if(player==null){logout(id);continue;}
            for(var maid:new ArrayList<>(WATCHES.get(id).keySet()))update(player,maid,false);
        }
    }
    private static void update(ServerPlayer player,UUID id,boolean select){
        var watches=WATCHES.get(player.getUUID());var previous=watches.get(id);
        var maid=loaded(player.server,id);var entry=SkyfarerMaidRegistry.get(player.server).find(id);
        UUID owner=maid!=null?maid.getOwnerUUID():entry!=null?entry.owner():previous.getUUID("owner");
        if(!allowed(player,owner)){remove(player,id);return;}
        CompoundTag data;
        if(maid!=null&&maid.isAlive()){
            var controller=SkyfarerRuntime.get(maid);data=CommandManager.debug(maid);
            if(data==null)data=controller==null?DebugSnapshot.make(maid,AirCombatState.GROUNDED,null,null,null,0,maid.getLookAngle(),0,AirRoute.EMPTY):controller.debugSnapshot();
            data.putBoolean("unloaded",false);data.putDouble("width",maid.getBbWidth());data.putDouble("height",maid.getBbHeight());
        }else{if(previous.getBoolean("unloaded")&&!select)return;data=previous.copy();data.putBoolean("unloaded",true);}
        data.putUUID("owner",owner);data.remove("select");watches.put(id,data);
        var packet=data.copy();if(select)packet.putBoolean("select",true);
        PacketDistributor.sendToPlayer(player,new AirCombatDebugPayload(packet));
    }
}
