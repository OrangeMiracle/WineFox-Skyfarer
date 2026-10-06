package dev.winefox.skyfarer.command;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.*;
import java.util.*;

/** Temporary rolling entity-ticking tickets. Never retain a complete journey's chunks. */
public final class CommandTickets {
    public static final TicketController CONTROLLER=new TicketController(ResourceLocation.fromNamespaceAndPath(WineFoxSkyfarer.MOD_ID,"command_flight"),
        (level,helper)->{helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);});
    private record Held(ServerLevel level,Set<Long> chunks,boolean complete){}
    private static final Map<UUID,Held> HELD=new HashMap<>();
    public static void register(RegisterTicketControllersEvent event){event.register(CONTROLLER);}
    public static void update(ServerLevel level,UUID maid,Vec3 position,Vec3 toward) {
        update(level,maid,position,toward,48*dev.winefox.skyfarer.flight.PredictionHorizon.factor());
    }
    public static void update(ServerLevel level,UUID maid,Vec3 position,Vec3 toward,double lookahead) {
        Set<Long> wanted=new HashSet<>();
        for(var cell:TicketWindow.around(position.x,position.z,toward.x-position.x,toward.z-position.z,lookahead))wanted.add(ChunkPos.asLong(cell.x(),cell.z()));
        replace(level,maid,wanted);
    }
    public static boolean wake(ServerLevel level,UUID maid,net.minecraft.core.BlockPos pos){return replace(level,maid,Set.of(new ChunkPos(pos).toLong()));}
    public static void recall(ServerLevel level,UUID maid,net.minecraft.core.BlockPos from,net.minecraft.core.BlockPos home) {
        var cells=new HashSet<Long>();cells.add(new ChunkPos(from).toLong());
        var center=new ChunkPos(home);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)cells.add(ChunkPos.asLong(center.x+x,center.z+z));
        replace(level,maid,cells);
    }
    public static boolean ready(UUID maid) {
        var held=HELD.get(maid);if(held==null||!held.complete||held.chunks.isEmpty())return false;
        for(long key:held.chunks){var p=new ChunkPos(key);if(!held.level.isPositionEntityTicking(new net.minecraft.core.BlockPos(p.x*16,64,p.z*16)))return false;}
        return true;
    }
    private static boolean replace(ServerLevel level,UUID maid,Set<Long> wanted) {
        var old=HELD.get(maid);if(old!=null&&old.level!=level){release(maid);old=null;}
        Set<Long> prior=old==null?Set.of():old.chunks;
        var accepted=new HashSet<>(prior);boolean success=true;
        for(long key:wanted)if(!prior.contains(key)){var p=new ChunkPos(key);if(CONTROLLER.forceChunk(level,maid,p.x,p.z,true,true))accepted.add(key);else success=false;}
        for(long key:prior)if(!wanted.contains(key)){var p=new ChunkPos(key);CONTROLLER.forceChunk(level,maid,p.x,p.z,false,true);}
        accepted.retainAll(wanted);HELD.put(maid,new Held(level,Set.copyOf(accepted),success));
        if(dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging()&&(!accepted.equals(prior)||!success))
            WineFoxSkyfarer.LOGGER.info("[WFS debug] COMMAND_TICKET maid={} stage=replace wanted={} held={} success={}",maid,wanted.size(),accepted.size(),success);
        return success;
    }
    public static void release(UUID maid){var old=HELD.remove(maid);if(old!=null){
        for(long key:old.chunks){var p=new ChunkPos(key);CONTROLLER.forceChunk(old.level,maid,p.x,p.z,false,true);}
        if(dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging())WineFoxSkyfarer.LOGGER.info("[WFS debug] COMMAND_TICKET maid={} stage=released chunks={}",maid,old.chunks.size());
    }}
    public static void clear(){for(var id:new ArrayList<>(HELD.keySet()))release(id);}
}
