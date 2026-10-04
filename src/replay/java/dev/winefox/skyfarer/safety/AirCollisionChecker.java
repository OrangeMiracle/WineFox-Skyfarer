package dev.winefox.skyfarer.safety;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;import net.minecraft.world.phys.Vec3;
public class AirCollisionChecker {
 public static double groundBelow(EntityMaid m,Vec3 p,double depth){double y=m.world.ground(p.x,p.z,p.y);return p.y-y<=depth?y:Double.NaN;}
 public static Vec3 landingNear(EntityMaid m,Vec3 p){double y=m.world.ground(p.x+4,p.z,p.y+8);return Double.isFinite(y)?new Vec3(p.x+4,y+.05,p.z):null;}
 public static boolean clear(EntityMaid m,Vec3 a,Vec3 b){return m.world.ray(a,b);}
}
