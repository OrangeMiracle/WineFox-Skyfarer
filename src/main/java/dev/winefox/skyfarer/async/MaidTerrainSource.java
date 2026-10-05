package dev.winefox.skyfarer.async;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.FlightCorridor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** The only terrain adapter. Its methods are called exclusively by PlannerSession's owner thread. */
public final class MaidTerrainSource implements PlannerSession.Source {
    private final EntityMaid maid;
    private final Thread owner=Thread.currentThread();
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("World snapshot read outside owner thread");}
    private int reads;
    @Override public int lastReads(){return reads;}
    public MaidTerrainSource(EntityMaid maid){this.maid=maid;}
    @Override public String dimension(){check();return maid.level().dimension().location().toString();}
    @Override public void failed(String message){check();dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.error("[WFS] Async planner failed; result discarded: {}",message);}
    @Override public CollisionSnapshot.Cell capture(long key){
        check();
        reads=0;int cx=CollisionSnapshot.x(key)*4,cy=CollisionSnapshot.y(key)*4,cz=CollisionSnapshot.z(key)*4;
        var level=maid.level();var solid=new ArrayList<FlightCorridor.Box>();var water=new ArrayList<FlightCorridor.Box>();long waterBits=0;
        if(level instanceof ServerLevel server){
            boolean empty=true;
            for(int x=Math.floorDiv(cx-1,16);x<=Math.floorDiv(cx+4,16);x++)for(int z=Math.floorDiv(cz-1,16);z<=Math.floorDiv(cz+4,16);z++){
                var chunk=server.getChunkSource().getChunkNow(x,z);
                if(chunk==null||!server.isPositionEntityTicking(new BlockPos(x*16,cy,z*16)))return new CollisionSnapshot.Cell(List.of(),List.of(),0,false);
                for(int y=Math.floorDiv(cy-1,16);y<=Math.floorDiv(cy+4,16);y++){
                    int index=chunk.getSectionIndex(y*16);
                    if(index>=0&&index<chunk.getSectionsCount()&&!chunk.getSection(index).hasOnlyAir())empty=false;
                }
            }
            if(empty)return new CollisionSnapshot.Cell(List.of(),List.of(),0,true);
        }
        var context=CollisionContext.of(maid);var pos=new BlockPos.MutableBlockPos();
        // Halo includes fences and state-dependent shapes protruding out of their owning voxel.
        for(int x=cx-1;x<=cx+4;x++)for(int y=cy-1;y<=cy+4;y++)for(int z=cz-1;z<=cz+4;z++){
            pos.set(x,y,z);
            if(!level.hasChunkAt(pos)||level instanceof ServerLevel server&&!server.isPositionEntityTicking(pos))return new CollisionSnapshot.Cell(List.of(),List.of(),0,false);
            reads++;var state=level.getBlockState(pos);
            if(!state.isAir())for(var b:state.getCollisionShape(level,pos,context).toAabbs())solid.add(new FlightCorridor.Box(x+b.minX,y+b.minY,z+b.minZ,x+b.maxX,y+b.maxY,z+b.maxZ));
            var fluid=state.getFluidState();
            if(!fluid.isEmpty()){
                boolean wet=fluid.is(FluidTags.WATER);var box=new FlightCorridor.Box(x,y,z,x+1,y+fluid.getHeight(level,pos),z+1);
                if(wet){water.add(box);if(x>=cx&&x<cx+4&&y>=cy&&y<cy+4&&z>=cz&&z<cz+4)waterBits|=1L<<((x-cx)*16+(y-cy)*4+z-cz);}else solid.add(box);
            }
        }
        return new CollisionSnapshot.Cell(solid,water,waterBits,true);
    }
    @Override public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){
        check();
        var border=maid.level().getWorldBorder();
        return new CollisionSnapshot(cells,maid.getBbWidth(),launch?.6:maid.getBbHeight(),launch?.4:maid.getEyeHeight(),wet,
            border.getMinX(),border.getMinZ(),border.getMaxX(),border.getMaxZ());
    }
}
