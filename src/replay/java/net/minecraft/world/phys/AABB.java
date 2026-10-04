package net.minecraft.world.phys;
import java.util.Optional;
public class AABB {
 public final double minX,minY,minZ,maxX,maxY,maxZ;
 public AABB(double a,double b,double c,double d,double e,double f){minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;}
 public AABB(Vec3 a,Vec3 b){this(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));}
 public AABB inflate(double n){return inflate(n,n,n);} public AABB inflate(double x,double y,double z){return new AABB(minX-x,minY-y,minZ-z,maxX+x,maxY+y,maxZ+z);}
 public Vec3 getCenter(){return new Vec3((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2);}
 public boolean intersects(AABB b){return maxX>=b.minX&&minX<=b.maxX&&maxY>=b.minY&&minY<=b.maxY&&maxZ>=b.minZ&&minZ<=b.maxZ;}
 public Optional<Vec3> clip(Vec3 a,Vec3 b){
  double lo=0,hi=1; double[] from={a.x,a.y,a.z},to={b.x,b.y,b.z},mn={minX,minY,minZ},mx={maxX,maxY,maxZ};
  for(int i=0;i<3;i++){double d=to[i]-from[i]; if(Math.abs(d)<1e-10){if(from[i]<mn[i]||from[i]>mx[i])return Optional.empty();}else{double t=(mn[i]-from[i])/d,u=(mx[i]-from[i])/d;lo=Math.max(lo,Math.min(t,u));hi=Math.min(hi,Math.max(t,u));if(lo>hi)return Optional.empty();}}
  return Optional.of(a.lerp(b,lo));
 }
}
