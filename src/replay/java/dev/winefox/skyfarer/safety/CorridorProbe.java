package dev.winefox.skyfarer.safety;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.*;import net.minecraft.world.phys.Vec3;import net.minecraft.core.BlockPos;
/** Same swept body math as production; boxes replace Minecraft block shape reads. */
public class CorridorProbe implements GlidePlanner.Corridor {
 private final EntityMaid maid;private final double width,height,eye;
 public CorridorProbe(EntityMaid m){this(m,Double.NaN,Double.NaN,Double.NaN);}
 public CorridorProbe(EntityMaid m,double w,double h,double e){maid=m;width=w;height=h;eye=e;}
 public CorridorProbe(EntityMaid m,double w,double h,double e,boolean wet){this(m,w,h,e);}
 public boolean clear(FlightPrediction.State a,FlightPrediction.State b,double side,double vertical){return clear(new Vec3(a.x(),a.y(),a.z()),new Vec3(b.x(),b.y(),b.z()),side,vertical);}
 public boolean clear(Vec3 a,Vec3 b,double side,double vertical){
  if(!maid.world.hasChunkAt(BlockPos.containing(a))||!maid.world.hasChunkAt(BlockPos.containing(b)))return false;
  double w=Double.isFinite(width)?width:maid.getBbWidth(),h=Double.isFinite(height)?height:maid.getBbHeight(),e=Double.isFinite(eye)?eye:maid.getEyeHeight();
  for(var box:maid.world.boxes)if(FlightCorridor.intersects(box,a.x,a.y,a.z,b.x,b.y,b.z,w/2,h,e,side,vertical))return false;return true;
 }
 public boolean segment(Vec3 a,Vec3 b,double side,double vertical){return clear(a,b,side,vertical);}
 public boolean lane(Vec3 a,Vec3 b,Vec3 c,double s,double v){return segment(a,b,s,v)&&segment(b,c,s,v);}
 public double supportSample(double x,double z,double y){return SupportSurface.nearest(maid.world.boxes,x,z,y,1.26,maid.getBbHeight());}
 public double supportNear(double x,double z,double y){return supportSample(x,z,y);}
}
