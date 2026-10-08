package dev.winefox.skyfarer.safety;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AirCollisionChecker {
    public static boolean sweptBodyClear(EntityMaid maid, Vec3 from, Vec3 to) {
        if (!loaded(maid, from, to)) return false;
        double radius = maid.getBbWidth() * 0.5 + 0.08;
        for (double x : new double[]{-radius, radius}) for (double z : new double[]{-radius, radius})
            for (double y : new double[]{0.05, maid.getBbHeight() - 0.05})
                if (!clear(maid, from.add(x, y, z), to.add(x, y, z))) return false;
        return maid.level().noCollision(maid, maid.getBoundingBox().move(to.subtract(maid.position())));
    }
    /** Actual collision surface, including slabs, checked only in already loaded chunks. */
    public static double groundBelow(EntityMaid maid, Vec3 point, double depth) {
        Vec3 end = point.add(0, -depth, 0);
        if (!loaded(maid, point, end)) return Double.NaN;
        var hit = maid.level().clip(new ClipContext(point, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, maid));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
    }
    public static boolean bodyPathClear(EntityMaid maid, Vec3 direction, double distance) {
        Vec3 delta = direction.normalize().scale(distance);
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize().scale(maid.getBbWidth() * 0.5 + 0.1);
        Vec3 base = maid.position().add(0, 0.25, 0);
        return clear(maid, base, base.add(delta))
            && clear(maid, base.add(side), base.add(side).add(delta))
            && clear(maid, base.subtract(side), base.subtract(side).add(delta));
    }
    public static Vec3 landingNear(EntityMaid maid, Vec3 owner) {
        Vec3 best = null; double bestDistance = Double.MAX_VALUE;
        for (int dx : new int[]{-4, 0, 4}) for (int dz : new int[]{-4, 0, 4}) {
            if (dx == 0 && dz == 0) continue;
            Vec3 above = owner.add(dx, 8, dz), below = above.add(0, -16, 0);
            if (!loaded(maid, above, below)) continue;
            var hit = maid.level().clip(new ClipContext(above, below, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, maid));
            if (hit.getType() == HitResult.Type.MISS || !maid.level().getFluidState(hit.getBlockPos()).isEmpty()) continue;
            Vec3 point = hit.getLocation().add(0, 0.05, 0);
            // Leave standing room after the gliding dimensions are restored.
            var standing = maid.getType().getDimensions().makeBoundingBox(point);
            if (!maid.level().noCollision(maid, standing)) continue;
            double distance = maid.position().distanceToSqr(point);
            if (distance < bestDistance) { best = point; bestDistance = distance; }
        }
        return best;
    }
    public static boolean supportedLanding(EntityMaid maid,double x,double y,double z){
        var point=new Vec3(x,y,z);
        if(!loaded(maid,point.add(0,.3,0),point.add(0,-.3,0)))return false;
        var floor=groundBelow(maid,point.add(0,.3,0),.6);
        if(!Double.isFinite(floor)||Math.abs(y-floor)>.1)return false;
        if(!maid.level().getFluidState(BlockPos.containing(x,floor+.01,z)).isEmpty())return false;
        return maid.level().noCollision(maid,maid.getType().getDimensions().makeBoundingBox(point));
    }
    public static boolean loaded(EntityMaid maid, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 4));
        for (int i = 0; i <= steps; i++) if (!maid.level().hasChunkAt(BlockPos.containing(from.lerp(to, i / (double)steps)))) return false;
        return true;
    }
    public static boolean clear(EntityMaid maid, Vec3 from, Vec3 to) {
        return loaded(maid, from, to) && maid.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid)).getType() == HitResult.Type.MISS;
    }
    public static boolean takeoffSpace(EntityMaid maid, Vec3 forward) {
        return takeoffBlockReason(maid, forward).isEmpty();
    }
    public static String takeoffBlockReason(EntityMaid maid, Vec3 forward) {
        if (maid.isInWaterOrBubble()) return "IN_WATER";
        String headroom = obstruction(maid, maid.getEyePosition(), maid.getEyePosition().add(0, 4, 0));
        if (!headroom.isEmpty()) return "HEADROOM: " + headroom;
        String ahead = obstruction(maid, maid.getEyePosition().add(0, 1, 0), maid.getEyePosition().add(forward.scale(5)).add(0, 3, 0));
        return ahead.isEmpty() ? "" : "LAUNCH_PATH: " + ahead;
    }
    private static String obstruction(EntityMaid maid, Vec3 from, Vec3 to) {
        if (!loaded(maid, from, to)) return "UNLOADED_CHUNK";
        var hit = maid.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        if (hit.getType() == HitResult.Type.MISS) return "";
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(maid.level().getBlockState(hit.getBlockPos()).getBlock()) + " at " + hit.getBlockPos().toShortString();
    }
}
