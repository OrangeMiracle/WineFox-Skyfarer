package dev.winefox.skyfarer.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.HeadingController;
import dev.winefox.skyfarer.safety.AirCollisionChecker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.monster.Phantom;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;

public final class TargetEvaluator {
    public record ScanResult(TargetSelection selection, int scanned, int withinSearch, int valid) {}
    public static boolean attackable(EntityMaid maid, LivingEntity target) {
        var owner = maid.getOwner();
        return target != null && target != maid && target != owner && !(target instanceof net.minecraft.world.entity.player.Player) && owner != null && owner.level() == maid.level() && target.level() == maid.level()
            && target.isAlive() && !target.isRemoved() && !target.isSpectator() && !target.isInvulnerable()
            && (CommandAccess.designated.test(maid,target) || !maid.isAlliedTo(target) && maid.canAttack(target));
    }
    public static boolean valid(EntityMaid maid, LivingEntity target, boolean pursuing, Vec3 anchor) {
        return attackable(maid, target) && (CommandAccess.marked(maid,target)&&maid.distanceTo(target)<=64 || target.position().distanceTo(anchor)
            <= (pursuing ? hardRange(maid) : combatRange(maid)));
    }
    public static double protectedRadius(EntityMaid maid) { return TargetPriority.protectedRadius(maid.isHomeModeEnable(),maid.getRestrictRadius()); }
    public static double combatRange(EntityMaid maid) { return Math.max(SkyfarerSettings.combatRange(),protectedRadius(maid)); }
    public static double hardRange(EntityMaid maid) { return Math.max(SkyfarerSettings.hardCombatRange(),combatRange(maid)+16); }
    public static Vec3 aimPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter().add(0,SkyfarerSettings.get("attack_aim_offset"),0);
    }
    /** Acquisition requires a visible body point; keep this separate from locked-pass validity. */
    public static boolean visible(EntityMaid maid,LivingEntity target) {
        if(target==null) return false;
        Vec3 center=target.getBoundingBox().getCenter();
        return AirCollisionChecker.clear(maid,maid.getEyePosition(),center)
            || AirCollisionChecker.clear(maid,maid.getEyePosition(),new Vec3(center.x,target.getY()+target.getBbHeight()*.85,center.z));
    }
    private static TargetPriority.Rank rank(EntityMaid maid,LivingEntity target,UUID previous,Vec3 anchor) {
        return TargetPriority.rank(CommandAccess.marked(maid,target),target.getUUID().equals(previous),target.position().distanceTo(anchor),protectedRadius(maid),
            target instanceof Phantom,score(maid,target,previous,anchor));
    }
    public static ScanResult scan(EntityMaid maid, LivingEntity preferred, UUID previous, Vec3 anchor) {
        return scan(maid, preferred, previous, anchor, Set.of());
    }
    /** Same strategic layers as scan; route quality is decided by the launch simulator. */
    public static java.util.List<LivingEntity> launchTargets(EntityMaid maid,LivingEntity preferred,UUID previous,Vec3 anchor){
        Set<LivingEntity> candidates=new LinkedHashSet<>(maid.level().getEntitiesOfClass(LivingEntity.class,maid.getBoundingBox().inflate(40)));
        candidates.addAll(CommandAccess.targets.apply(maid));if(preferred!=null)candidates.add(preferred);
        var legal=candidates.stream().filter(t->valid(maid,t,true,anchor)&&visible(maid,t)).toList();
        var layer=legal.stream().map(t->rank(maid,t,previous,anchor)).max(TargetPriority.Rank::compareTo).orElse(null);
        if(layer==null)return java.util.List.of();
        return legal.stream().filter(t->rank(maid,t,previous,anchor).sameLayer(layer))
            .filter(t->t.getY()+t.getBbHeight()*.5>maid.getY()+1.82&&maid.distanceTo(t)<=40)
            .sorted(java.util.Comparator.comparingDouble(t->maid.distanceTo(t))).limit(4).toList();
    }
    public static ScanResult scan(EntityMaid maid, LivingEntity preferred, UUID previous, Vec3 anchor, Set<UUID> avoided) {
        LivingEntity best = null; double bestScore = -Double.MAX_VALUE;
        TargetPriority.Rank bestRank=null;
        int scanned = 0, withinSearch = 0, validCount = 0;
        double searchRange = SkyfarerSettings.searchRange();
        Set<LivingEntity> candidates = new LinkedHashSet<>(maid.level().getEntitiesOfClass(LivingEntity.class, maid.getBoundingBox().inflate(searchRange)));
        candidates.addAll(CommandAccess.targets.apply(maid));
        // Query loaded entities around the persistent work center even after the last victim died.
        candidates.addAll(maid.level().getEntitiesOfClass(LivingEntity.class,new AABB(anchor,anchor).inflate(combatRange(maid))));
        // Withdrawal can carry the maid beyond local scan range. Remember the loaded combat area.
        if (preferred != null && preferred.level() == maid.level())
            candidates.addAll(maid.level().getEntitiesOfClass(LivingEntity.class, preferred.getBoundingBox().inflate(searchRange)));
        for (LivingEntity candidate : candidates) {
            if (candidate == maid) continue;
            scanned++;
            if (!CommandAccess.marked(maid,candidate) && maid.distanceTo(candidate) > searchRange && candidate.position().distanceTo(anchor)>combatRange(maid)
                && (preferred == null || candidate.distanceTo(preferred) > searchRange)) continue;
            withinSearch++;
            if (!valid(maid, candidate, candidate.getUUID().equals(previous), anchor) || !visible(maid,candidate)) continue;
            validCount++;
            if (avoided.contains(candidate.getUUID())) continue;
            var ranking=rank(maid,candidate,previous,anchor);
            if (bestRank==null || ranking.compareTo(bestRank)>0) { best=candidate; bestScore=ranking.route(); bestRank=ranking; }
        }
        if (valid(maid, preferred, true, anchor) && visible(maid,preferred) && !avoided.contains(preferred.getUUID())) {
            var oldRank=rank(maid,preferred,previous,anchor);
            if (best==null || best==preferred || oldRank.compareTo(bestRank)>0
                || (oldRank.sameLayer(bestRank) && bestScore<oldRank.route()+SkyfarerSettings.targetSwitchThreshold()))
                return new ScanResult(new TargetSelection(preferred,oldRank.route()),scanned,withinSearch,validCount);
        }
        // With no alternative, retry the available enemy; a new alternative can replace it next scan.
        if (best == null && !avoided.isEmpty()) return scan(maid, preferred, previous, anchor, Set.of());
        return new ScanResult(new TargetSelection(best, best == null ? 0 : bestScore), scanned, withinSearch, validCount);
    }
    public static LivingEntity secondary(EntityMaid maid, LivingEntity primary, Vec3 incoming, Vec3 anchor) {
        double radius = SkyfarerSettings.get("secondary_target_radius");
        if (primary == null || radius <= 0) return null;
        LivingEntity best = null; double cost = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : maid.level().getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(radius))) {
            if (candidate == primary || !valid(maid, candidate, true, anchor) || candidate.distanceTo(primary) > radius) continue;
            Vec3 offset = candidate.position().subtract(primary.position());
            Vec3 line = offset.horizontalDistanceSqr() < 0.01 ? incoming : HeadingController.horizontal(offset);
            if (line.dot(incoming) < 0) line = line.scale(-1);
            double angle = HeadingController.horizontalAngle(line, incoming);
            // A secondary opportunity must never force a sharp turn or compromise the primary pass.
            if (angle > Math.min(25, SkyfarerSettings.diveMaxCorrectionAngle())
                || !AirCollisionChecker.clear(maid, primary.getBoundingBox().getCenter(), candidate.getBoundingBox().getCenter())) continue;
            double candidateCost = angle * 2 + candidate.distanceTo(primary);
            if (candidateCost < cost) { best = candidate; cost = candidateCost; }
        }
        return best;
    }
    public static double score(EntityMaid maid, LivingEntity target, UUID previous, Vec3 anchor) {
        Vec3 momentum=maid.getDeltaMovement().horizontalDistanceSqr()>.04?maid.getDeltaMovement():maid.getLookAngle();
        double angle = HeadingController.horizontalAngle(momentum, target.position().subtract(maid.position()));
        boolean visible = visible(maid,target);
        double anchorDistance = target.position().distanceTo(anchor);
        return 100 - maid.distanceTo(target) - angle * 0.2 - anchorDistance * 0.25 + (visible ? 15 : -30)
            +(CommandAccess.marked(maid,target)?SkyfarerSettings.get("command_mark_bonus"):0);
    }
}
