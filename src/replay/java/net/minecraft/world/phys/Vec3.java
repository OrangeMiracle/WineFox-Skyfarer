package net.minecraft.world.phys;
import net.minecraft.core.BlockPos;
public class Vec3 {
 public final double x,y,z; public static final Vec3 ZERO=new Vec3(0,0,0);
 public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
 public Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);} public Vec3 add(Vec3 p){return add(p.x,p.y,p.z);}
 public Vec3 subtract(Vec3 p){return add(-p.x,-p.y,-p.z);} public Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
 public double dot(Vec3 p){return x*p.x+y*p.y+z*p.z;} public double lengthSqr(){return dot(this);} public double length(){return Math.sqrt(lengthSqr());}
 public double horizontalDistanceSqr(){return x*x+z*z;} public double horizontalDistance(){return Math.hypot(x,z);}
 public Vec3 normalize(){return length()<1e-8?ZERO:scale(1/length());} public double distanceTo(Vec3 p){return subtract(p).length();}
 public double distanceToSqr(Vec3 p){return subtract(p).lengthSqr();} public Vec3 lerp(Vec3 p,double a){return add(p.subtract(this).scale(a));}
 public static Vec3 atBottomCenterOf(BlockPos p){return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
 public String toString(){return "("+x+","+y+","+z+")";}
}
