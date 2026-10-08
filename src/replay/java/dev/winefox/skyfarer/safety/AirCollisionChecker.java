package dev.winefox.skyfarer.safety;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;import net.minecraft.world.phys.Vec3;
public class AirCollisionChecker {
 public static double groundBelow(EntityMaid m,Vec3 p,double depth){double y=m.world.ground(p.x,p.z,p.y);return p.y-y<=depth?y:Double.NaN;}
 public static Vec3 landingNear(EntityMaid m,Vec3 p){double y=m.world.ground(p.x+4,p.z,p.y+8);return Double.isFinite(y)&&y>=p.y-8?new Vec3(p.x+4,y+.05,p.z):null;}
 public static boolean clear(EntityMaid m,Vec3 a,Vec3 b){return m.world.ray(a,b);}
 public static boolean supportedLanding(EntityMaid m,double x,double y,double z){
  double floor=m.world.ground(x,z,y+.3);var eye=new Vec3(x,y+1.5,z);
  return Double.isFinite(floor)&&Math.abs(y-floor)<=.1&&new CorridorProbe(m,m.getBbWidth(),1.8,1.5).clear(eye,eye,0,0);
 }
}
