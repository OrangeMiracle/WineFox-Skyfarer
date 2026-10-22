package dev.winefox.skyfarer.weapon;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.AirCombatState;
import dev.winefox.skyfarer.ai.CombatAnchor;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.*;

/** Passive server-thread weapon. It never writes flight state, heading, targets or propulsion. */
public final class Fox3System {
    private static final String LAST_CHECK = "wfs_fox3_last_check";
    private static final String MESSAGE = "message.winefox_skyfarer.fox3.";
    private static final Fox3Budget BUDGET = new Fox3Budget();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private Fox3System() {}

    private record ShotSettings(int waves, int interval, int missiles, double damage, double radius, boolean consume) {
        static ShotSettings current() {
            return new ShotSettings(SkyfarerSettings.fox3Waves(), SkyfarerSettings.fox3WaveIntervalTicks(),
                SkyfarerSettings.fox3MissilesPerWave(), SkyfarerSettings.fox3Damage(), SkyfarerSettings.fox3BlastRadius(),
                SkyfarerSettings.fox3ConsumeFireworks());
        }
    }
    private static final class Session {
        final EntityMaid maid;
        final Set<Fox3MissileEntity> rockets = new HashSet<>();
        final Map<UUID, Integer> assignments = new HashMap<>();
        Fox3Salvo salvo;
        ShotSettings settings;
        Vec3 anchor;
        long scanAt;
        boolean announced;
        Session(EntityMaid maid) { this.maid = maid; }
    }
    private static long now(EntityMaid maid) { return ((ServerLevel)maid.level()).getServer().overworld().getGameTime(); }
    private static Session session(EntityMaid maid) {
        Session old = SESSIONS.get(maid.getUUID());
        if (old != null && old.maid != maid) cancel(old.maid);
        return SESSIONS.computeIfAbsent(maid.getUUID(), id -> new Session(maid));
    }
    public static boolean searchBudget(EntityMaid maid) {
        return maid.level() instanceof ServerLevel && BUDGET.search(now(maid));
    }

    /** Entity POST observes the completed flight decision without changing its control ownership. */
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel)) return;
        if (!maid.isAlive() || maid.isRemoved() || !SkyfarerRuntime.isSkyfarer(maid)) { cancel(maid); return; }
        long now = now(maid);
        var data = maid.getPersistentData();
        if (!data.contains(LAST_CHECK) || now < data.getLong(LAST_CHECK)) data.putLong(LAST_CHECK, now);
        Session state = session(maid);
        if (state.salvo != null) {
            if (!safeFlight(maid, false) || !state.salvo.active(now)) state.salvo = null;
            else if (state.salvo.due(now)) fireWave(state, now, null);
            // Completing a manual salvo must not also draw the automatic lottery in this tick.
            return;
        }
        if (!SkyfarerSettings.fox3Enabled() || !safeFlight(maid, true) || now < state.scanAt
            || !Fox3Salvo.automaticDue(now, data.getLong(LAST_CHECK), SkyfarerSettings.fox3CooldownTicks())) return;
        Vec3 anchor = CombatAnchor.current(maid);
        var targets = Fox3Targets.scan(maid, anchor);
        state.scanAt = now + (targets == null ? 1 + Math.floorMod(maid.getUUID().hashCode(), 7) : 20);
        if (targets == null || targets.size() < SkyfarerSettings.fox3MinMonsters()) return;
        // Store BEFORE the lottery/spawn callback: both failure and success enter a full cooldown.
        data.putLong(LAST_CHECK, now);
        if (maid.getRandom().nextDouble() < SkyfarerSettings.fox3Chance()) start(state, anchor, targets, now);
    }

    /** The caller (DebugPanelManager) owns nonce/UUID/permissions; this owns actual launch eligibility. */
    public static String manualLaunch(EntityMaid maid) {
        if (maid == null || !(maid.level() instanceof ServerLevel) || !maid.isAlive() || maid.isRemoved()) return MESSAGE + "unavailable";
        if (!safeFlight(maid, true)) return MESSAGE + "unsafe_flight";
        Session state = session(maid);
        long now = now(maid);
        if (state.salvo != null && state.salvo.active(now)) return MESSAGE + "busy";
        if (!BUDGET.available(maid.getUUID(), now)) return MESSAGE + "capacity";
        if (SkyfarerSettings.fox3ConsumeFireworks() && !Fox3Ammo.available(maid.getAvailableInv(false), true)) return MESSAGE + "no_ammo";
        Vec3 anchor = CombatAnchor.current(maid);
        var targets = Fox3Targets.scan(maid, anchor);
        if (targets == null) return MESSAGE + "capacity";
        if (targets.isEmpty()) return MESSAGE + "no_targets";
        start(state, anchor, targets, now);
        return MESSAGE + (state.announced ? "manual_started" : "unavailable");
    }

    private static boolean safeFlight(EntityMaid maid, boolean starting) {
        var owner = maid.getOwner();
        if (!maid.isAlive() || maid.isRemoved() || !SkyfarerRuntime.isSkyfarer(maid) || !SkyfarerRuntime.isAirControlActive(maid)
            || !maid.isFallFlying() || maid.onGround() || maid.isInWaterOrBubble() || maid.isNoAi() || maid.guiOpening
            || maid.isMaidInSittingPose() || maid.isSleeping() || maid.isPassenger() || maid.isLeashed()
            || owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != maid.level()) return false;
        var controller = SkyfarerRuntime.get(maid);
        if (controller == null || !controller.controlsAir()) return true; // independently controlled transit flight
        AirCombatState phase = controller.state();
        return phase != AirCombatState.TAKEOFF && phase != AirCombatState.TAKEOFF_PREP && phase != AirCombatState.WATER_ESCAPE
            && phase != AirCombatState.LANDING && (!starting || !phase.locksRunTarget());
    }
    private static void start(Session state, Vec3 anchor, List<LivingEntity> targets, long now) {
        state.settings = ShotSettings.current();
        state.anchor = anchor;
        state.assignments.clear();
        state.announced = false;
        state.salvo = new Fox3Salvo(state.settings.waves, state.settings.interval, now);
        fireWave(state, now, targets);
    }
    private static void fireWave(Session state, long now, List<LivingEntity> initialTargets) {
        var maid = state.maid;
        var targets = initialTargets == null ? Fox3Targets.scan(maid, state.anchor) : initialTargets;
        if (targets == null) return; // shared budget: defer this wave, never spin or catch up
        for (int i = 0; i < state.settings.missiles; i++) {
            if (state.salvo == null || SESSIONS.get(maid.getUUID()) != state) return;
            LivingEntity target = Fox3Targets.choose(maid, targets, state.assignments);
            if (target == null || !BUDGET.reserve(maid.getUUID(), now)) break;
            Vec3 forward = maid.getDeltaMovement();
            if (forward.horizontalDistanceSqr() < 0.01) forward = maid.getLookAngle();
            forward = new Vec3(forward.x, 0, forward.z).normalize();
            if (forward.lengthSqr() < 0.01) forward = new Vec3(0, 0, 1);
            double side = state.settings.missiles == 1 ? 0 : (2.0 * i / (state.settings.missiles - 1) - 1.0);
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            // Just behind each wing; no launch-position teleport through an obstruction.
            Vec3 position = maid.getBoundingBox().getCenter().add(forward.scale(-0.55)).add(right.scale(side * 0.65)).add(0, 0.25, 0);
            var launch = Fox3Guidance.launchVelocity(forward.x, forward.z, side);
            var rocket = new Fox3MissileEntity(maid, target, state.anchor, position, new Vec3(launch.x(), launch.y(), launch.z()), state.settings.damage, state.settings.radius);
            state.rockets.add(rocket);
            rocket.onRemoval(() -> { state.rockets.remove(rocket); BUDGET.release(maid.getUUID()); });
            var level = maid.level();
            var owner = maid.getOwnerUUID();
            boolean launched = Fox3Ammo.launch(state.settings.consume ? maid.getAvailableInv(false) : null, state.settings.consume, () -> {
                var launchBounds = rocket.getBoundingBox().minmax(maid.getBoundingBox());
                if (!level.hasChunksAt(net.minecraft.core.BlockPos.containing(launchBounds.minX, launchBounds.minY, launchBounds.minZ),
                    net.minecraft.core.BlockPos.containing(launchBounds.maxX, launchBounds.maxY, launchBounds.maxZ))
                    || !level.noCollision(rocket, rocket.getBoundingBox())) return false;
                var path = level.clip(new net.minecraft.world.level.ClipContext(maid.getBoundingBox().getCenter(), position,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, maid));
                if (path.getType() != net.minecraft.world.phys.HitResult.Type.MISS || !level.addFreshEntity(rocket)) return false;
                return !rocket.isRemoved() && maid.level() == level && Objects.equals(owner, maid.getOwnerUUID()) && safeFlight(maid, false);
            }, rocket::discard);
            if (!launched) { rocket.discard(); continue; }
            state.assignments.merge(target.getUUID(), 1, Integer::sum);
            if (!state.announced) { state.announced = true; announce(maid); }
        }
        if (state.salvo == null || SESSIONS.get(maid.getUUID()) != state) return;
        state.salvo.fired(now);
        if (!state.salvo.active(now)) state.salvo = null;
    }
    private static void announce(EntityMaid maid) {
        var message = Component.literal("<").append(maid.getDisplayName()).append("> ")
            .append(Component.translatable(MESSAGE + (maid.getRandom().nextBoolean() ? "launch" : "launch_alt")))
            .withStyle(style -> style.withColor(0xFF8800).withBold(true));
        // One announcement per salvo, visible to its owner and nearby players in the same dimension.
        for (ServerPlayer player : ((ServerLevel)maid.level()).players())
            if (Objects.equals(player.getUUID(), maid.getOwnerUUID()) || player.distanceToSqr(maid) <= 64 * 64) player.sendSystemMessage(message);
    }
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) cancel(maid);
    }
    public static void cancel(EntityMaid maid) {
        Session state = SESSIONS.get(maid.getUUID());
        if (state == null || state.maid != maid) return;
        SESSIONS.remove(maid.getUUID());
        state.salvo = null;
        for (var rocket : List.copyOf(state.rockets)) rocket.discard();
    }
    public static void clear() {
        for (var state : List.copyOf(SESSIONS.values())) cancel(state.maid);
        SESSIONS.clear(); BUDGET.clear();
    }
}
