package net.minecraft.world.entity.projectile;
import dev.winefox.skyfarer.replay.ReplayWorld;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
public class FireworkRocketEntity implements dev.winefox.skyfarer.mixin.FireworkLifetimeAccessor {
 public final EntityMaid maid; public int life,lifetime; public boolean alive=true;
 public FireworkRocketEntity(ReplayWorld w,ItemStack item,EntityMaid m){maid=m;lifetime=10*(1+item.duration)+w.rocketJitter;}
 public boolean isAlive(){return alive;} public boolean isRemoved(){return !alive;} public void discard(){alive=false;}
 public int wfs$lifetime(){return lifetime;} public int wfs$life(){return life;}
}
