package dev.winefox.skyfarer.flight;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;import net.minecraft.world.entity.Pose;
public class ElytraFlightController {
 public static boolean startGliding(EntityMaid m){return startGliding(m,false);} public static boolean startGliding(EntityMaid m,boolean water){if(m.ground||!m.equipped)return false;m.glide=true;m.pose=Pose.FALL_FLYING;return true;}
 public static void stopGliding(EntityMaid m){m.glide=false;m.pose=Pose.STANDING;}
}
