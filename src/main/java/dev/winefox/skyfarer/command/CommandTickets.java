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
    private record Held(ServerLevel level,Set<Long> chunks){}
    private static final Map<UUID,Held> HELD=new HashMap<>();
    public static void register(RegisterTicketControllersEvent event){event.register(CONTROLLER);}
    public static void update(ServerLevel level,UUID maid,Vec3 position,Vec3 toward) {
        Set<Long> wanted=new HashSet<>();
        for(var cell:TicketWindow.around(position.x,position.z,toward.x-position.x,toward.z-position.z))wanted.add(ChunkPos.asLong(cell.x(),cell.z()));
        replace(level,maid,wanted);
    }
    public static void wake(ServerLevel level,UUID maid,net.minecraft.core.BlockPos pos){replace(level,maid,Set.of(new ChunkPos(pos).toLong()));}
    public static boolean ready(UUID maid) {
        var held=HELD.get(maid);if(held==null)return false;
        for(long key:held.chunks){var p=new ChunkPos(key);if(!held.level.isPositionEntityTicking(new net.minecraft.core.BlockPos(p.x*16,64,p.z*16)))return false;}
        return true;
    }
    private static void replace(ServerLevel level,UUID maid,Set<Long> wanted) {
        var old=HELD.get(maid);if(old!=null&&old.level!=level){release(maid);old=null;}
        Set<Long> prior=old==null?Set.of():old.chunks;
        for(long key:wanted)if(!prior.contains(key)){var p=new ChunkPos(key);CONTROLLER.forceChunk(level,maid,p.x,p.z,true,true);}
        for(long key:prior)if(!wanted.contains(key)){var p=new ChunkPos(key);CONTROLLER.forceChunk(level,maid,p.x,p.z,false,true);}
        HELD.put(maid,new Held(level,wanted));
    }
    public static void release(UUID maid){var old=HELD.remove(maid);if(old!=null)for(long key:old.chunks){var p=new ChunkPos(key);CONTROLLER.forceChunk(old.level,maid,p.x,p.z,false,true);}}
    public static void clear(){for(var id:new ArrayList<>(HELD.keySet()))release(id);}
}
