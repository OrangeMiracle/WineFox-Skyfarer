package net.minecraft.core;
import net.minecraft.world.phys.Vec3;
public record BlockPos(int x,int y,int z) {
 public int getY(){return y;}
 public BlockPos above(int up){return new BlockPos(x,y+up,z);}
 public static BlockPos containing(double x,double y,double z){return new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}
 public static BlockPos containing(Vec3 p){return containing(p.x,p.y,p.z);}
}
