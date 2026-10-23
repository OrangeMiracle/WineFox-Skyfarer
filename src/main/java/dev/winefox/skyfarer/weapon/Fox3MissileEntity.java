package dev.winefox.skyfarer.weapon;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Independent, ephemeral server-owned guided rocket. Only native firework data/interfaces are
 * inherited: FireworkRocketEntity.tick and its unfiltered native explosion damage never run.
 */
public final class Fox3MissileEntity extends FireworkRocketEntity {
    public static final int COLLISION_CANDIDATES = 64;
    public static final int DAMAGE_CANDIDATES = 128;
    public static final int ORANGE = 0xFF7800;
    private static final List<FireworkExplosion> EXPLOSIONS = List.of(new FireworkExplosion(
        FireworkExplosion.Shape.SMALL_BALL, IntList.of(ORANGE, 0xFFAA20), IntList.of(0xFF4500), true, false));
    private UUID maidId;
    private UUID ownerId;
    private EntityMaid sourceIdentity;
    private UUID targetId;
    private Vec3 anchor = Vec3.ZERO;
    private Vec3 launchVelocity = new Vec3(0, Fox3Guidance.LAUNCH_SPEED, 0);
    private Vec3 rolloverPoint = Vec3.ZERO;
    private Fox3Guidance.Phase phase = Fox3Guidance.Phase.CLIMB;
    private double damage = 5;
    private double radius = 2;
    private int age;
    private int nextRetarget;
    private boolean detonated;
    private Runnable removal = () -> {};
    private Vec3 clientTarget;
    private int clientLerpSteps;
    private static long trailTick = Long.MIN_VALUE;
    private static int trailParticles;

    public Fox3MissileEntity(EntityType<? extends Fox3MissileEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public Fox3MissileEntity(EntityMaid maid, LivingEntity target, Vec3 anchor, Vec3 launchPosition,
                             Vec3 launchVelocity, double damage, double radius) {
        this(Fox3Entities.MISSILE.get(), maid.level());
        this.maidId = maid.getUUID();
        this.ownerId = maid.getOwnerUUID();
        this.sourceIdentity = maid;
        this.targetId = target == null ? null : target.getUUID();
        this.anchor = anchor;
        this.launchVelocity = launchVelocity.normalize().scale(Fox3Guidance.LAUNCH_SPEED);
        Vec3 initialAim = target == null ? anchor : target.getBoundingBox().getCenter();
        this.rolloverPoint = new Vec3(initialAim.x, Math.min(initialAim.y, launchPosition.y - 4.0), initialAim.z);
        this.damage = Math.clamp(damage, 0.0, 100.0);
        this.radius = Math.clamp(radius, 0.1, 8.0);
        setOwner(maid);
        setPos(launchPosition);
        setDeltaMovement(this.launchVelocity);
        nextRetarget = Fox3Guidance.RETARGET_TICKS + Math.floorMod(getUUID().hashCode(), 20);
    }

    public UUID maidUuid() { return maidId; }
    public void onRemoval(Runnable callback) { removal = callback; }

    @Override public void remove(RemovalReason reason) {
        // Also covers a spawn cancellation before the entity ever receives a level callback.
        releaseOnce();
        super.remove(reason);
    }

    @Override public void onRemovedFromLevel() {
        // Chunk unload calls final Entity.setRemoved directly, bypassing remove(). NeoForge's
        // tracking-end callback still reaches this hook, so unloaded rockets release their slots.
        releaseOnce();
        super.onRemovedFromLevel();
    }

    private void releaseOnce() {
        Runnable callback = removal;
        removal = () -> {};
        callback.run();
    }

    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean canChangeDimensions(Level from, Level to) { return false; }
    @Override public boolean isShotAtAngle() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128.0 * 128.0; }

    @Override public void tick() {
        // baseTick has normal entity housekeeping, but no firework acceleration or damage.
        baseTick();
        if (isRemoved()) return;
        age++;
        if (level().isClientSide) { clientTick(); return; }
        if (!(level() instanceof ServerLevel server) || maidId == null || age > Fox3Guidance.MAX_LIFE_TICKS) {
            discard(); return;
        }
        Entity source = server.getEntity(maidId); // Loaded entity lookup only; never requests a chunk.
        if (!(source instanceof EntityMaid maid) || source != sourceIdentity || !maid.isAlive() || maid.isRemoved()
            || maid.level() != level() || !Objects.equals(ownerId, maid.getOwnerUUID())) {
            discard(); return;
        }
        if (age == 1) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.5F, 1.0F);
            gameEvent(GameEvent.PROJECTILE_SHOOT, maid);
        }
        Vec3 rolloverDirection = rolloverPoint.subtract(position());
        phase = Fox3Guidance.nextPhase(phase, age, vector(getDeltaMovement()), vector(rolloverDirection));
        Vec3 desired = getDeltaMovement();
        if (phase == Fox3Guidance.Phase.CLIMB) desired = toVec3(Fox3Guidance.climbDirection(vector(launchVelocity), age));
        else if (phase == Fox3Guidance.Phase.ROLLOVER) desired = rolloverDirection;
        else {
            // Acquisition and moving-target steering begin only after the outward climb and rollover.
            LivingEntity target = resolveTarget(server, maid);
            if (target != null) {
            // Short bounded lead prevents a stationary aim point from trailing a moving target.
            Vec3 center = target.getBoundingBox().getCenter();
            double lead = Math.min(4.0, position().distanceTo(center) / Fox3Guidance.MAX_SPEED * 0.35);
            desired = center.add(target.getDeltaMovement().scale(lead)).subtract(position());
            }
        }
        Vec3 velocity = toVec3(Fox3Guidance.step(vector(getDeltaMovement()), vector(desired), phase));
        setDeltaMovement(velocity);
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        // Include the complete local entity sweep margin; all collision queries remain loaded-only.
        if (!loaded(getBoundingBox().expandTowards(velocity).inflate(1.0))) { discard(); return; }
        Vec3 impact = firstImpact(start, end, maid);
        if (impact != null) {
            Vec3 outside = impact.subtract(velocity.normalize().scale(0.015));
            setPos(outside);
            explodeFox3(maid);
            return;
        }
        setPos(end); // The bounded, fully swept step, never a target-position teleport.
        updateRotation();
        hasImpulse = true;
    }

    private LivingEntity resolveTarget(ServerLevel server, EntityMaid maid) {
        Entity entity = targetId == null ? null : server.getEntity(targetId);
        LivingEntity target = entity instanceof LivingEntity living && Fox3Targets.legal(maid, living)
            && living.position().distanceToSqr(anchor) <= 96.0 * 96.0 ? living : null;
        if (target == null && age >= nextRetarget) {
            nextRetarget = age + Fox3Guidance.RETARGET_TICKS + Math.floorMod(getUUID().hashCode(), 7);
            target = Fox3Targets.choose(maid, Fox3Targets.scan(maid, anchor, position()), Map.of());
            targetId = target == null ? null : target.getUUID();
        }
        return target;
    }

    /** Native voxel-shape ray clip plus native AABB segment clips for physical entity obstacles. */
    private Vec3 firstImpact(Vec3 start, Vec3 end, EntityMaid maid) {
        HitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 blockHit = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        List<Entity> entities = new ArrayList<>();
        level().getEntities(EntityTypeTest.forClass(Entity.class), getBoundingBox().expandTowards(end.subtract(start)).inflate(0.3),
            e -> true, entities, COLLISION_CANDIDATES);
        // Fail closed in an exceptionally dense cell; never tunnel through unexamined obstacles.
        if (entities.size() >= COLLISION_CANDIDATES) return start;
        List<AABB> obstacles = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity == this || entity instanceof Fox3MissileEntity || entity.isSpectator() || !entity.canBeHitByProjectile()) continue;
            if (age <= Fox3Guidance.CLIMB_TICKS && (entity == maid || entity == maid.getOwner())) continue;
            obstacles.add(entity.getBoundingBox().inflate(0.125));
        }
        return Fox3Impact.nearest(start, end, blockHit, obstacles);
    }

    private boolean loaded(AABB bounds) {
        return level().hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
            BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
    }

    private void explodeFox3(EntityMaid maid) {
        if (detonated || isRemoved() || level().isClientSide) return;
        detonated = true;
        try {
            // Event 17 has no coordinates. Send the authoritative contact point first, then let the
            // client burst at that queued position rather than its deliberately interpolated rocket.
            ((ServerLevel)level()).getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));
            level().broadcastEntityEvent(this, (byte)17);
            gameEvent(GameEvent.EXPLODE, maid);
            List<LivingEntity> candidates = new ArrayList<>();
            level().getEntities(EntityTypeTest.forClass(LivingEntity.class), getBoundingBox().inflate(radius),
                e -> true, candidates, DAMAGE_CANDIDATES);
            Fox3Blast.apply(candidates, position(), radius, damage, Entity::getUUID, victim -> Fox3Targets.legal(maid, victim),
                Entity::getBoundingBox, victim -> {
                    Vec3 middle = victim.getBoundingBox().getCenter();
                    Vec3 lower = new Vec3(victim.getX(), victim.getY() + Math.min(0.25, victim.getBbHeight() * 0.25), victim.getZ());
                    return visible(middle) || visible(lower);
                }, (victim, amount) -> victim.hurt(damageSources().fireworks(this, maid), (float)amount));
        } finally {
            // Third-party damage/event callbacks must not leave a detonated entity holding a slot.
            discard();
        }
    }

    private boolean visible(Vec3 to) {
        if (!loaded(new AABB(position(), to))) return false;
        return level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == 17) {
            if (level().isClientSide) {
                Vec3 burst = clientTarget == null ? position() : clientTarget;
                level().createFireworks(burst.x, burst.y, burst.z, 0, 0, 0, EXPLOSIONS);
            }
        } else super.handleEntityEvent(event);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        clientTarget = new Vec3(x, y, z);
        clientLerpSteps = Math.max(1, Math.min(3, steps));
    }

    private float visualYaw, visualPitch, previousVisualYaw, previousVisualPitch;
    private boolean visualInitialized;
    public float visualYaw(float partial) {
        return visualInitialized ? net.minecraft.util.Mth.rotLerp(partial,previousVisualYaw,visualYaw)
            : Fox3VisualOrientation.fromVelocity(getDeltaMovement(),0,0).yaw();
    }
    public float visualPitch(float partial) {
        return visualInitialized ? net.minecraft.util.Mth.lerp(partial,previousVisualPitch,visualPitch)
            : Fox3VisualOrientation.fromVelocity(getDeltaMovement(),0,0).pitch();
    }
    private void clientTick() {
        var orientation = Fox3VisualOrientation.fromVelocity(getDeltaMovement(),visualYaw,visualPitch);
        previousVisualYaw = visualInitialized ? visualYaw : orientation.yaw();
        previousVisualPitch = visualInitialized ? visualPitch : orientation.pitch();
        visualYaw=orientation.yaw(); visualPitch=orientation.pitch(); visualInitialized=true;
        Vec3 previous = position();
        if (clientLerpSteps > 0 && clientTarget != null) {
            setPos(previous.lerp(clientTarget, 1.0 / clientLerpSteps));
            clientLerpSteps--;
        } else setPos(previous.add(getDeltaMovement()));
        updateRotation();
        long now = level().getGameTime();
        if (now != trailTick) { trailTick = now; trailParticles = 0; }
        // Native white spark particles last about 48-60 ticks, leaving a long continuous trail.
        for (int i = 0; i < 2 && trailParticles < 128; i++) {
            float fraction = i * 0.5F;
            Vec3 point = previous.lerp(position(), fraction).add(Fox3VisualOrientation.nozzle(visualYaw(fraction),visualPitch(fraction)));
            level().addParticle(ParticleTypes.FIREWORK, point.x, point.y, point.z,
                -getDeltaMovement().x * 0.025, -getDeltaMovement().y * 0.025, -getDeltaMovement().z * 0.025);
            trailParticles++;
        }
    }

    private static Fox3Guidance.Vector vector(Vec3 value) { return new Fox3Guidance.Vector(value.x, value.y, value.z); }
    private static Vec3 toVec3(Fox3Guidance.Vector value) { return new Vec3(value.x(), value.y(), value.z()); }
}
