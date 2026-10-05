package dev.winefox.skyfarer.async;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.FlightCorridor;
import java.util.*;

/** External world adapter only. Actual session, executor, DTOs and planners run unchanged. */
public final class MaidTerrainSource implements PlannerSession.Source {
    private final EntityMaid maid;private final Thread owner=Thread.currentThread();
    private int reads;
    public int lastReads(){return reads;}
    public MaidTerrainSource(EntityMaid maid){this.maid=maid;}
    public Object levelIdentity(){return maid.level();}
    public String dimension(){return maid.world.dimension().location();}
    public CollisionSnapshot.Cell capture(long key){
        if(Thread.currentThread()!=owner)throw new AssertionError("World read from worker");
        int x=CollisionSnapshot.x(key)*4,y=CollisionSnapshot.y(key)*4,z=CollisionSnapshot.z(key)*4;
        boolean loaded=Math.abs(x)+4<maid.world.loadedRadius&&Math.abs(z)+4<maid.world.loadedRadius;
        var boxes=new ArrayList<FlightCorridor.Box>();
        for(var b:maid.world.boxes)if(b.maxX()>=x-1&&b.minX()<=x+5&&b.maxY()>=y-1&&b.minY()<=y+5&&b.maxZ()>=z-1&&b.minZ()<=z+5)boxes.add(b);
        reads=boxes.isEmpty()?0:216;return new CollisionSnapshot.Cell(boxes,List.of(),0,loaded);
    }
    public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){double r=maid.world.loadedRadius;return new CollisionSnapshot(cells,maid.getBbWidth(),launch?.6:maid.getBbHeight(),launch?.4:maid.getEyeHeight(),wet,-r,-r,r,r);}
}
