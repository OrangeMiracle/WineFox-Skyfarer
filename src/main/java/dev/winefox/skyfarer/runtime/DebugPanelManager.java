package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.command.*;
import dev.winefox.skyfarer.mixin.MaidWorldDataAccessor;
import dev.winefox.skyfarer.network.DebugPanelPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.world.chunk.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-owned panel sessions and bounded, temporary chunk wakeups for explicit debug teleports. */
public final class DebugPanelManager {
    private static final TicketController TICKETS=new TicketController(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID,"debug_remote"),
        (level,helper)->{helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);});
    private static final class Session {
        final UUID nonce=UUID.randomUUID();UUID owner;String query="";int page,size=4,requests;long expires,last=-1;boolean initial=true;
        Session(UUID owner,long now){this.owner=owner;expires=now+12000;}
    }
    private record Pending(UUID ticket,UUID player,UUID maid,UUID owner,UUID session,ServerLevel level,Set<Long> chunks,boolean bring,long expires){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    public static void registerTickets(RegisterTicketControllersEvent event){event.register(TICKETS);}
    public static void open(ServerPlayer player){
        var session=new Session(player.getUUID(),player.server.overworld().getGameTime());SESSIONS.put(player.getUUID(),session);send(player,session);
    }
    private static boolean holding(ServerPlayer player){return player.getMainHandItem().is(WineFoxSkyfarer.DEBUG_STICK.get())||player.getOffhandItem().is(WineFoxSkyfarer.DEBUG_STICK.get());}
    public static void action(ServerPlayer player,CompoundTag request){
        var session=SESSIONS.get(player.getUUID());long now=player.server.overworld().getGameTime();
        if(session==null||!request.hasUUID("session")||!session.nonce.equals(request.getUUID("session"))||now>session.expires||!holding(player)){message(player,"调试面板已过期，请重新右键打开");return;}
        if(session.last!=now){session.last=now;session.requests=0;}if(++session.requests>20)return;session.expires=now+12000;
        if(!DebugWatchManager.allowed(player,session.owner)){session.owner=player.getUUID();session.page=0;}
        String action=request.getString("action");
        if(action.equals("list")){
            if(request.hasUUID("owner")&&DebugWatchManager.allowed(player,request.getUUID("owner")))session.owner=request.getUUID("owner");
            session.query=request.getString("query");if(session.query.length()>64)session.query=session.query.substring(0,64);
            session.page=Math.max(0,request.getInt("page"));session.size=Math.clamp(request.getInt("size"),1,20);
        }else if(request.hasUUID("maid")){
            var registry=SkyfarerMaidRegistry.get(player.server);var entry=registry.find(request.getUUID("maid"));
            if(entry==null&&action.equals("bind")&&!request.getBoolean("enabled"))entry=DebugWatchManager.cachedEntries(player).stream().filter(e->e.id().equals(request.getUUID("maid"))).findFirst().orElse(null);
            if(entry==null||!entry.owner().equals(session.owner)||!DebugWatchManager.allowed(player,entry.owner())){message(player,"女仆记录失效或没有管理权限");send(player,session);return;}
            if(action.equals("bind"))DebugWatchManager.set(player,entry,request.getBoolean("enabled"));
            else if(action.equals("visit")||action.equals("bring"))startTeleport(player,session,entry,action.equals("bring"));
        }
        send(player,session);
    }
    private static String ownerName(MinecraftServer server,UUID owner){
        var online=server.getPlayerList().getPlayer(owner);if(online!=null)return online.getGameProfile().getName();
        var cache=server.getProfileCache();return cache==null?owner.toString():cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
    }
    private static void send(ServerPlayer player,Session session){
        if(!DebugWatchManager.allowed(player,session.owner)){session.owner=player.getUUID();session.page=0;}
        var registry=SkyfarerMaidRegistry.get(player.server);registry.seed(player.server,session.owner);
        // Observe loaded records without loading additional chunks or scanning other dimensions' terrain.
        for(var level:player.server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof EntityMaid maid&&session.owner.equals(maid.getOwnerUUID()))registry.observe(maid);
        var data=new CompoundTag();data.putUUID("session",session.nonce);data.putUUID("owner",session.owner);data.putString("owner_name",ownerName(player.server,session.owner));data.putString("query",session.query);
        var owners=new HashSet<UUID>();owners.add(player.getUUID());
        if(player.hasPermissions(2)){
            owners.addAll(registry.owners());owners.addAll(((MaidWorldDataAccessor)MaidWorldData.get(player.server.overworld())).skyfarer$infos().keySet());
            for(var online:player.server.getPlayerList().getPlayers())owners.add(online.getUUID());
        }
        var names=new HashMap<UUID,String>();for(var id:owners)names.put(id,ownerName(player.server,id));
        var ownerTags=new ListTag();String query=session.query.toLowerCase(Locale.ROOT);
        owners.stream().filter(id->names.get(id).toLowerCase(Locale.ROOT).contains(query)||id.toString().contains(query))
            .sorted(Comparator.comparing(names::get,String.CASE_INSENSITIVE_ORDER)).limit(512).forEach(id->{var t=new CompoundTag();t.putUUID("id",id);t.putString("name",names.get(id));ownerTags.add(t);});
        data.put("owners",ownerTags);data.putBoolean("admin",player.hasPermissions(2));
        var entries=registry.owned(session.owner);var present=new HashSet<UUID>();for(var e:entries)present.add(e.id());
        for(var e:DebugWatchManager.cachedEntries(player))if(e.owner().equals(session.owner)&&present.add(e.id()))entries.add(e);
        entries.sort(Comparator.comparing(SkyfarerMaidRegistry.Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(SkyfarerMaidRegistry.Entry::id));
        session.page=Math.min(session.page,Math.max(0,(entries.size()-1)/session.size));
        data.putInt("page",session.page);data.putInt("size",session.size);data.putInt("total",entries.size());
        var rows=new ListTag();for(int i=session.page*session.size;i<Math.min(entries.size(),(session.page+1)*session.size);i++){
            var e=entries.get(i);var t=new CompoundTag();t.putUUID("id",e.id());t.putString("name",e.name().isEmpty()?"女仆":e.name());t.putString("dimension",e.dimension());
            var maid=DebugWatchManager.loaded(player.server,e.id());Vec3 pos=maid==null?Vec3.atBottomCenterOf(e.pos()):maid.position();DebugSnapshot.vector(t,"position",pos);
            t.putBoolean("loaded",maid!=null);t.putBoolean("bound",DebugWatchManager.bound(player,e.id()));
            t.putDouble("distance",player.level().dimension().location().toString().equals(e.dimension())?player.position().distanceTo(pos):-1);rows.add(t);
        }data.put("maids",rows);data.putBoolean("busy",PENDING.containsKey(player.getUUID()));
        data.putBoolean("open",session.initial);session.initial=false;
        PacketDistributor.sendToPlayer(player,new DebugPanelPayload(data));
    }
    private static void startTeleport(ServerPlayer player,Session session,SkyfarerMaidRegistry.Entry entry,boolean bring){
        if(PENDING.containsKey(player.getUUID())){message(player,"上一次传送正在加载女仆，请稍候");return;}
        var dim=ResourceLocation.tryParse(entry.dimension());var level=dim==null?null:player.server.getLevel(ResourceKey.create(Registries.DIMENSION,dim));
        if(level==null){message(player,"女仆所在维度不可用");return;}
        UUID ticket=UUID.randomUUID();var chunks=new HashSet<Long>();var center=new ChunkPos(entry.pos());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){chunks.add(ChunkPos.asLong(center.x+x,center.z+z));TICKETS.forceChunk(level,ticket,center.x+x,center.z+z,true,true);}
        PENDING.put(player.getUUID(),new Pending(ticket,player.getUUID(),entry.id(),entry.owner(),session.nonce,level,chunks,bring,player.server.overworld().getGameTime()+100));
        message(player,"正在定位女仆并加载传送位置……");
    }
    public static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        for(var pending:new ArrayList<>(PENDING.values())){
            var player=server.getPlayerList().getPlayer(pending.player);var session=SESSIONS.get(pending.player);
            if(player==null||session==null||!session.nonce.equals(pending.session)||!DebugWatchManager.allowed(player,pending.owner)){finish(pending);continue;}
            var maid=DebugWatchManager.loaded(server,pending.maid);
            if(maid!=null&&maid.isAlive()){
                if(!pending.owner.equals(maid.getOwnerUUID())){message(player,"女仆所属玩家已改变，传送已取消");finish(pending);send(player,session);continue;}
                var level=pending.bring?player.serverLevel():(ServerLevel)maid.level();
                var moving=pending.bring?maid:player;Vec3 center=pending.bring?player.position():maid.position();
                var destination=landing(level,moving,center);
                if(destination==null){if(now<pending.expires)continue;message(player,"女仆附近未找到可容纳身体的传送位置");}
                else {
                    boolean allowed=level==moving.level()||net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(moving,level.dimension());
                    if(allowed){if(pending.bring)CommandManager.prepareDebugTeleport(maid);moving.stopRiding();moving.ejectPassengers();}
                    // This is the vanilla command teleport API, not TLM's portal-specific changeDimension override.
                    boolean success=allowed&&moving.teleportTo(level,destination.x,destination.y,destination.z,Set.of(),moving.getYRot(),moving.getXRot());
                    var result=level.getEntity(moving.getUUID());if(success&&result!=null){result.setDeltaMovement(Vec3.ZERO);result.fallDistance=0;if(result instanceof EntityMaid moved)SkyfarerMaidRegistry.get(server).observe(moved);}
                    message(player,success?"传送完成":"传送被取消");
                }
                finish(pending);send(player,session);
            }else if(now>=pending.expires){message(player,"女仆未加载成功，记录可能已失效；临时区块已释放");finish(pending);send(player,session);}
        }
    }
    private static Vec3 landing(ServerLevel level,Entity entity,Vec3 center){
        Vec3 airFallback=null;
        for(int y:new int[]{0,1,-1,2,-2,3,-3,4,-4})for(int radius=2;radius<=5;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;var pos=BlockPos.containing(center).offset(x,y,z);
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.offset(1,0,1))||!level.hasChunkAt(pos.offset(-1,0,-1))||!level.getWorldBorder().isWithinBounds(pos))continue;
            var point=Vec3.atBottomCenterOf(pos);var box=new AABB(point.x-entity.getBbWidth()*.5,point.y,point.z-entity.getBbWidth()*.5,point.x+entity.getBbWidth()*.5,point.y+Math.max(entity.getBbHeight(),1.8),point.z+entity.getBbWidth()*.5);
            if(level.noCollision(entity,box)&&!level.containsAnyLiquid(box)){
                if(!level.noCollision(entity,box.move(0,-.1,0)))return point;
                if(airFallback==null)airFallback=point;
            }
        }return airFallback;
    }
    private static void finish(Pending p){PENDING.remove(p.player);for(long key:p.chunks){var chunk=new ChunkPos(key);TICKETS.forceChunk(p.level,p.ticket,chunk.x,chunk.z,false,true);}}
    public static void logout(UUID id){SESSIONS.remove(id);var pending=PENDING.get(id);if(pending!=null)finish(pending);}
    public static void clear(){for(var pending:new ArrayList<>(PENDING.values()))finish(pending);SESSIONS.clear();}
    private static void message(ServerPlayer player,String text){player.displayClientMessage(Component.literal(text),false);}
}
