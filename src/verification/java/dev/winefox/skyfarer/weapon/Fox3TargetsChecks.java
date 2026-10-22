package dev.winefox.skyfarer.weapon;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Executes the actual Fox3Targets methods against real MC/TLM entity subtypes. Constructors
 * and world IO are bypassed; only the world/health/ownership properties consumed by targeting
 * are supplied. This does not claim to run a live Minecraft entity tick or load any chunks. */
public final class Fox3TargetsChecks {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static long serial;

    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        initializeTlmType();
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        unsafe = (sun.misc.Unsafe)field.get(null);
        Level level = (ServerLevel)unsafe.allocateInstance(ServerLevel.class);
        Level otherLevel = (ServerLevel)unsafe.allocateInstance(ServerLevel.class);
        var maid = maid(level); var owner = entity(Hostile.class, level); maid.owner = owner;
        require(Fox3OfflineAccessAgent.applied("net/minecraft/world/entity/Entity", "getEyeHeight(Lnet/minecraft/world/entity/Pose;)F"), "test JVM applied TLM's real declared Entity access transform");
        var enemy = entity(Hostile.class, level);
        require(Fox3Targets.legal(maid, enemy), "ordinary living hostile is legal");
        require(!Fox3Targets.legal(maid, null), "null target rejected");
        require(!Fox3Targets.legal(maid, owner), "owner protected even if its subtype implements Enemy");
        require(!Fox3Targets.legal(maid, maid), "launcher protected even if its subtype implements Enemy");
        require(!Fox3Targets.legal(maid, maid(level)), "all maids protected even if hostile-tagged");
        require(!Fox3Targets.legal(maid, player(level)), "all players protected even if hostile-tagged");
        require(!Fox3Targets.legal(maid, entity(ProbeLiving.class, level)), "unmarked passive mob is not a monster");
        var pet = entity(OwnedHostile.class, level); pet.ownerId = owner.getUUID();
        require(!Fox3Targets.legal(maid, pet), "hostile-tagged owned pet protected");
        pet.ownerId = UUID.randomUUID(); require(!Fox3Targets.legal(maid, pet), "another owner's hostile pet also protected");
        pet.ownerId = null; require(Fox3Targets.legal(maid, pet), "unowned hostile subtype remains legal");

        enemy.s.alive = false; require(!Fox3Targets.legal(maid, enemy), "dead target rejected"); enemy.s.alive = true;
        var removed = Entity.class.getDeclaredField("removalReason"); removed.setAccessible(true);
        removed.set(enemy, Entity.RemovalReason.UNLOADED_TO_CHUNK);
        require(!Fox3Targets.legal(maid, enemy), "unloaded/removed entity rejected using actual final isRemoved"); removed.set(enemy, null);
        enemy.s.spectator = true; require(!Fox3Targets.legal(maid, enemy), "spectator target rejected"); enemy.s.spectator = false;
        enemy.s.invulnerable = true; require(!Fox3Targets.legal(maid, enemy), "invulnerable target rejected"); enemy.s.invulnerable = false;
        enemy.s.level = otherLevel; require(!Fox3Targets.legal(maid, enemy), "target in another dimension rejected"); enemy.s.level = level;
        maid.s.ally = enemy; require(!Fox3Targets.legal(maid, enemy), "maid-to-target alliance protected"); maid.s.ally = null;
        enemy.s.ally = maid; require(!Fox3Targets.legal(maid, enemy), "target-to-maid alliance protected"); enemy.s.ally = null;
        owner.s.ally = enemy; require(!Fox3Targets.legal(maid, enemy), "owner-to-target alliance protected"); owner.s.ally = null;
        enemy.s.ally = owner; require(!Fox3Targets.legal(maid, enemy), "target-to-owner alliance protected"); enemy.s.ally = null;
        maid.attackable = false; require(!Fox3Targets.legal(maid, enemy), "actual maid attack permission honored"); maid.attackable = true;
        maid.owner = null; require(!Fox3Targets.legal(maid, enemy), "missing owner ends target eligibility"); maid.owner = owner;
        owner.s.alive = false; require(!Fox3Targets.legal(maid, enemy), "dead owner ends eligibility"); owner.s.alive = true;
        owner.s.spectator = true; require(!Fox3Targets.legal(maid, enemy), "spectator owner ends eligibility"); owner.s.spectator = false;
        owner.s.level = otherLevel; require(!Fox3Targets.legal(maid, enemy), "owner dimension change ends eligibility"); owner.s.level = level;
        require(Fox3Targets.legal(maid, enemy), "eligibility restores after transient conditions clear");

        choose(maid, owner, level);
        flightGate(maid, level);
        releaseOnce();
        require(Fox3System.manualLaunch(null).endsWith("unavailable"), "actual manual entry rejects null entity safely");
        maid.s.alive = false;
        require(Fox3System.manualLaunch(maid).endsWith("unavailable"), "actual manual entry rejects dead entity before flight/session mutation");
        maid.s.alive = true; removed.set(maid, Entity.RemovalReason.DISCARDED);
        require(Fox3System.manualLaunch(maid).endsWith("unavailable"), "actual manual entry rejects removed entity");
        System.out.println("Fox3 real target filtering and choice: " + checks + " assertions passed (actual MC/TLM subclasses; no live world or client started).");
    }

    private static void releaseOnce() throws Exception {
        var rocket = (Fox3MissileEntity)unsafe.allocateInstance(Fox3MissileEntity.class);
        var release = Fox3MissileEntity.class.getDeclaredMethod("releaseOnce"); release.setAccessible(true);
        int[] callbacks = {0};
        rocket.onRemoval(() -> {
            callbacks[0]++;
            // Discard and level-removal hooks can reenter one another during world removal.
            try { release.invoke(rocket); } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        });
        rocket.onRemovedFromLevel(); release.invoke(rocket); rocket.onRemovedFromLevel();
        require(callbacks[0] == 1, "actual missile release is exactly once across repeated and reentrant lifecycle hooks");
        var failing = (Fox3MissileEntity)unsafe.allocateInstance(Fox3MissileEntity.class);
        int[] failures = {0}; failing.onRemoval(() -> { failures[0]++; throw new IllegalStateException("fixture callback failure"); });
        try { release.invoke(failing); throw new AssertionError("expected fixture failure"); }
        catch (java.lang.reflect.InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException, "callback fixture propagated expected failure"); }
        release.invoke(failing);
        require(failures[0] == 1, "a throwing removal callback is not repeated on the second hook");
    }

    /** A real mod loader initializes TLM before registry freeze. The vanilla-only Bootstrap above
     * has already frozen registries, so allow just TLM's static, unregistered TYPE holder creation,
     * then restore the original registry bookkeeping. No entity type is inserted or registered. */
    private static void initializeTlmType() throws Exception {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        var intrusive = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"); intrusive.setAccessible(true);
        boolean beforeFrozen = frozen.getBoolean(registry); Object beforeIntrusive = intrusive.get(registry); int beforeSize = registry.size();
        try {
            frozen.setBoolean(registry, false); intrusive.set(registry, new IdentityHashMap<>());
            Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", true, Fox3TargetsChecks.class.getClassLoader());
        } finally { intrusive.set(registry, beforeIntrusive); frozen.setBoolean(registry, beforeFrozen); }
        require(registry.size() == beforeSize && frozen.getBoolean(registry) == beforeFrozen
            && intrusive.get(registry) == beforeIntrusive, "TLM fixture initialization restores registry size/freeze/intrusive bookkeeping");
    }

    @SuppressWarnings("unchecked")
    private static void flightGate(ProbeMaid maid, Level level) throws Exception {
        var mapField = dev.winefox.skyfarer.runtime.SkyfarerRuntime.class.getDeclaredField("CONTROLLERS"); mapField.setAccessible(true);
        var controllers = (Map<UUID,dev.winefox.skyfarer.ai.AirCombatController>)mapField.get(null);
        var controller = (dev.winefox.skyfarer.ai.AirCombatController)unsafe.allocateInstance(dev.winefox.skyfarer.ai.AirCombatController.class);
        var managed = controller.getClass().getDeclaredField("managed"); managed.setAccessible(true); managed.setBoolean(controller, true);
        var phase = controller.getClass().getDeclaredField("state"); phase.setAccessible(true);
        var gate = Fox3System.class.getDeclaredMethod("safeFlight", EntityMaid.class, boolean.class); gate.setAccessible(true);
        controllers.put(maid.getUUID(), controller);
        try {
            phase.set(controller, dev.winefox.skyfarer.ai.AirCombatState.APPROACH);
            require((boolean)gate.invoke(null, maid, true), "actual safe-flight gate permits ordinary controlled glide");
            for (var committed : List.of(dev.winefox.skyfarer.ai.AirCombatState.ATTACK_RUN, dev.winefox.skyfarer.ai.AirCombatState.DIVE,
                dev.winefox.skyfarer.ai.AirCombatState.SPEAR_CHARGE, dev.winefox.skyfarer.ai.AirCombatState.CHAIN_ATTACK)) {
                phase.set(controller, committed);
                require(!(boolean)gate.invoke(null, maid, true), "actual start gate rejects committed " + committed);
                require((boolean)gate.invoke(null, maid, false), "already-fired salvo may continue during " + committed);
                require(Fox3System.manualLaunch(maid).endsWith("unsafe_flight"), "actual manual entry respects committed " + committed);
            }
            phase.set(controller, dev.winefox.skyfarer.ai.AirCombatState.LANDING);
            require(!(boolean)gate.invoke(null, maid, false), "landing cancels even ongoing launch");
            phase.set(controller, dev.winefox.skyfarer.ai.AirCombatState.APPROACH);
            maid.s.flying = false;
            require(Fox3System.manualLaunch(maid).endsWith("unsafe_flight"), "actual manual entry rejects non-flying maid"); maid.s.flying = true;
            maid.s.grounded = true;
            require(Fox3System.manualLaunch(maid).endsWith("unsafe_flight"), "actual manual entry rejects grounded maid"); maid.s.grounded = false;
            maid.guiOpening = true;
            require(Fox3System.manualLaunch(maid).endsWith("unsafe_flight"), "actual manual entry rejects inventory interaction"); maid.guiOpening = false;
            var other = maid(level); other.owner = maid.owner;
            require(!(boolean)gate.invoke(null, other, true), "another maid cannot borrow first maid's controller by UUID");
            require((boolean)gate.invoke(null, maid, true), "first maid remains eligible after testing another UUID");
        } finally { controllers.remove(maid.getUUID()); }
    }

    private static void choose(ProbeMaid maid, Hostile owner, Level level) throws Exception {
        var near = entity(Hostile.class, level); near.s.position = new Vec3(10, 0, 0);
        var far = entity(Hostile.class, level); far.s.position = new Vec3(28, 0, 0);
        require(Fox3Targets.choose(maid, List.of(far, near), Map.of()) == near, "owner-near monster wins over otherwise valid combat-area target");
        require(Fox3Targets.choose(maid, List.of(far), Map.of()) == far, "combat-area fallback when owner-near pool is empty");
        require(Fox3Targets.choose(maid, null, Map.of()) == null && Fox3Targets.choose(maid, List.of(), Map.of()) == null, "deferred/empty candidates produce no target");
        var passive = entity(ProbeLiving.class, level);
        require(Fox3Targets.choose(maid, List.of(owner, maid, player(level), passive, far), Map.of()) == far, "choice rechecks all protected/passive candidates");
        near.s.alive = false;
        require(Fox3Targets.choose(maid, List.of(near, far), Map.of()) == far, "dead prior target is replaced by a valid alternative"); near.s.alive = true;
        List<LivingEntity> pool = new ArrayList<>(); for (int i = 0; i < 8; i++) pool.add(entity(Hostile.class, level));
        var assignments = new HashMap<UUID,Integer>(); var picked = new HashSet<UUID>();
        for (int i = 0; i < 8; i++) {
            var target = Fox3Targets.choose(maid, pool, assignments);
            require(target != null && picked.add(target.getUUID()), "eight missiles disperse before reusing one of eight equal-priority enemies");
            assignments.merge(target.getUUID(), 1, Integer::sum);
        }
        var otherMaid = maid(level); otherMaid.owner = owner;
        require(Fox3Targets.choose(otherMaid, List.of(near, far), Map.of()) == near, "another maid uses its own assignment context");
        // ProbeMaid.getTarget throws: successful calls prove this route does not read or mutate
        // the main combat selector's target, irrespective of its marked strategic target.
        require(maid.targetReads == 0 && maid.targetWrites == 0, "Fox3 leaves the main target selector untouched");
    }

    private static State state(Level level) { var s = new State(); s.level = level; s.id = new UUID(0, ++serial); return s; }
    private static <T extends ProbeLiving> T entity(Class<T> type, Level level) throws Exception { var value = type.cast(unsafe.allocateInstance(type)); value.s = state(level); return value; }
    private static ProbeMaid maid(Level level) throws Exception { var value = (ProbeMaid)unsafe.allocateInstance(ProbeMaid.class); value.s = state(level); value.attackable = true; value.random = RandomSource.create(37); return value; }
    private static ProbePlayer player(Level level) throws Exception { var value = (ProbePlayer)unsafe.allocateInstance(ProbePlayer.class); value.s = state(level); return value; }
    private static void require(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static final class State { Level level; UUID id; boolean alive = true, flying = true, grounded, spectator, invulnerable; Entity ally; Vec3 position = Vec3.ZERO; }
    private interface StateView { State view(); }

    public static class ProbeLiving extends LivingEntity implements StateView {
        State s;
        protected ProbeLiving() { super(EntityType.ZOMBIE, null); }
        public State view() { return s; }
        @Override public Level level() { return s.level; }
        @Override public boolean isAlive() { return s.alive; }
        @Override public boolean isSpectator() { return s.spectator; }
        @Override public boolean isInvulnerable() { return s.invulnerable; }
        @Override public boolean isAlliedTo(Entity entity) { return entity == s.ally; }
        @Override public UUID getUUID() { return s.id; }
        @Override public Vec3 position() { return s.position; }
        @Override public double distanceToSqr(Entity entity) { return s.position.distanceToSqr(((StateView)entity).view().position); }
        @Override public Iterable<ItemStack> getArmorSlots() { return List.of(); }
        @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
        @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { throw new AssertionError("targeting must not change equipment"); }
        @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
    }
    public static class Hostile extends ProbeLiving implements Enemy {}
    public static final class OwnedHostile extends Hostile implements OwnableEntity {
        UUID ownerId;
        @Override public UUID getOwnerUUID() { return ownerId; }
    }
    public static final class ProbeMaid extends EntityMaid implements Enemy, StateView {
        State s; LivingEntity owner; boolean attackable; RandomSource random; int targetReads, targetWrites;
        private ProbeMaid() { super((Level)null); }
        public State view() { return s; }
        @Override public LivingEntity getOwner() { return owner; }
        @Override public com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask getTask() { return new dev.winefox.skyfarer.compat.tlm.SkyfarerTask(); }
        @Override public Level level() { return s.level; }
        @Override public boolean isAlive() { return s.alive; }
        @Override public boolean isSpectator() { return s.spectator; }
        @Override public boolean isInvulnerable() { return s.invulnerable; }
        @Override public boolean isAlliedTo(Entity entity) { return entity == s.ally; }
        @Override public boolean canAttack(LivingEntity entity) { return attackable; }
        @Override public UUID getUUID() { return s.id; }
        @Override public RandomSource getRandom() { return random; }
        @Override public boolean isFallFlying() { return s.flying; }
        @Override public boolean onGround() { return s.grounded; }
        @Override public boolean isInWaterOrBubble() { return false; }
        @Override public boolean isNoAi() { return false; }
        @Override public boolean isMaidInSittingPose() { return false; }
        @Override public boolean isSleeping() { return false; }
        @Override public boolean isPassenger() { return false; }
        @Override public boolean isLeashed() { return false; }
        @Override public LivingEntity getTarget() { targetReads++; throw new AssertionError("Fox3 must not read strategic target"); }
        @Override public void setTarget(LivingEntity target) { targetWrites++; throw new AssertionError("Fox3 must not change strategic target"); }
    }
    public static final class ProbePlayer extends Player implements Enemy, StateView {
        State s;
        private ProbePlayer() { super(null, null, 0, null); }
        public State view() { return s; }
        @Override public Level level() { return s.level; }
        @Override public boolean isAlive() { return s.alive; }
        @Override public boolean isSpectator() { return s.spectator; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isInvulnerable() { return s.invulnerable; }
        @Override public boolean isAlliedTo(Entity entity) { return entity == s.ally; }
        @Override public UUID getUUID() { return s.id; }
    }
}
