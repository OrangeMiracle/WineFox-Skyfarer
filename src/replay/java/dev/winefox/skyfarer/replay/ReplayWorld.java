package dev.winefox.skyfarer.replay;
import net.minecraft.world.entity.*;import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.*;import net.minecraft.core.BlockPos;
import dev.winefox.skyfarer.flight.FlightCorridor;
import java.util.*;
/** Offline external-world adapter: loaded bounds, exact boxes, clocks and inventories only. */
public class ReplayWorld {
 public void playSound(Object player,Object entity,Object sound,Object source,float volume,float pitch){}
 public static final List<String> events=new ArrayList<>();
 public final List<LivingEntity> entities=new ArrayList<>();public final List<FireworkRocketEntity> rockets=new ArrayList<>();
 public final List<FlightCorridor.Box> boxes=new ArrayList<>();public long time;public int rocketJitter=5;public double loadedRadius=128;
 public ReplayWorld(){boxes.add(new FlightCorridor.Box(-256,-20,-256,256,0,256));}
 public long getGameTime(){return time;}public boolean hasChunkAt(BlockPos p){return Math.abs(p.x())<loadedRadius&&Math.abs(p.z())<loadedRadius;}
 public void advance(long tick){time=tick;dev.winefox.skyfarer.async.PlannerSession.serverTick();try{Thread.sleep(Long.getLong("wfs.replayTickMillis",50L));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
 public int getMaxBuildHeight(){return 320;}public DimensionType dimensionType(){return new DimensionType();}public static class DimensionType{public boolean hasCeiling(){return false;}}
 public boolean addFreshEntity(FireworkRocketEntity e){rockets.add(e);return true;}
 public <T extends LivingEntity> List<T> getEntitiesOfClass(Class<T> type,AABB area){return getEntitiesOfClass(type,area,e->true);}
 public <T extends LivingEntity> List<T> getEntitiesOfClass(Class<T> type,AABB area,java.util.function.Predicate<T> test){return entities.stream().filter(type::isInstance).map(type::cast).filter(e->e.alive&&e.getBoundingBox().intersects(area)&&test.test(e)).toList();}
 public Fluid getFluidState(BlockPos pos){return new Fluid();} public static class Fluid {public boolean is(Object tag){return false;}}
 public Dimension dimension(){return new Dimension();}public static class Dimension {public String location(){return "replay:offline";}}
 public double ground(double x,double z,double below){double y=Double.NaN;for(var b:boxes)if(x>=b.minX()&&x<=b.maxX()&&z>=b.minZ()&&z<=b.maxZ()&&b.maxY()<=below+1e-5)y=Double.isNaN(y)?b.maxY():Math.max(y,b.maxY());return y;}
 public boolean ray(Vec3 a,Vec3 b){for(var box:boxes)if(new AABB(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()).clip(a,b).isPresent())return false;return true;}
}
