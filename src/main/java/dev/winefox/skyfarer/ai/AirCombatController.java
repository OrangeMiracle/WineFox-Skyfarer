package dev.winefox.skyfarer.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import dev.winefox.skyfarer.compat.tlm.SkyfarerEquipment;
import dev.winefox.skyfarer.compat.tlm.TlmFollowCompat;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.runtime.DebugSnapshot;
import dev.winefox.skyfarer.runtime.SkyfarerRuntime;
import dev.winefox.skyfarer.safety.AirCollisionChecker;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

public final class AirCombatController {
    private final EntityMaid maid;
    private final HeadingController heading = new HeadingController();
    private final FireworkManager fireworks = new FireworkManager();
    private final ScanCadence scanCadence = new ScanCadence(), logCadence = new ScanCadence();
    private final SpearPassHistory passHistory = new SpearPassHistory();
    private AirCombatState state = AirCombatState.GROUNDED;
    private AirRoute route = AirRoute.EMPTY;
    private LivingEntity preferred, runTarget;
    private UUID previous;
    private double score, clearance = Double.NaN;
    private boolean managed;
    private int stateTicks, airborneTicks, runTicks, returnTicks;
    private long retryAt;
    private Vec3 waypoint = Vec3.ZERO, runDirection = new Vec3(0, 0, 1);
    private Vec3 runOrigin = Vec3.ZERO, lastPosition, movement = Vec3.ZERO;
    private Vec3 landingPoint;
    private Vec3 combatAnchor;
    private LivingEntity secondaryTarget;
    private Vec3 multiDirection = Vec3.ZERO;
    private long chargeTailUntil = -1;
    private UUID motionTarget;
    private Vec3 lastTargetPosition, targetMotion = Vec3.ZERO;
    private int setupTicks, setupAttempts;
    private double runSlope;
    private double laneGradientX,laneGradientZ;
    private double runFlare=14;
    private Vec3 slopeEntry;
    private Vec3 debugEntry,debugEntryDirection;
    private CorridorProbe corridor;
    private FlightPrediction.State tickPrediction;
    private GlidePlanner.Request flightRequest;
    private GlidePlanner.Plan flightPlan;
    private double runHeightOffset;
    private Vec3 laneExit;
    private Vec3 recoveryOrigin;
    private String exitStyle = "STRAIGHT_CLIMB", laneStatus = "not checked";
    private double energyReserve = Double.NaN;
    private String diagnostic = "WAITING_FOR_SCAN", lastScan = "not scanned", pendingReturn = "";
    private String returnReason="";
    private SurfaceLaunchPlanner.Plan surfaceLaunch;
    private int launchControlTicks;
    private boolean previousWantToSwim;
    private String waterReturnReason="";
    private double exitDistanceLimit,exitTravelled;
    private SlopeSampler.Estimate sampledSlope;
    private UUID sampledTarget;
    private Vec3 sampledPosition;
    private long sampledAt;
    private double sampledRadius,sampledStep;
    private CompactTurnPlanner.Goal compactGoal;
    private CompactTurnPlanner.Plan compactPlan;
    private Vec3 chainCenter;
    private LivingEntity chainCandidate,chainTarget;
    private int chainTicks,chainHits;
    private double chainTravel;

    public AirCombatController(EntityMaid maid) { this.maid = maid; lastPosition = maid.position(); }
    public AirCombatState state() { return state; }
    public UUID previousTarget(){return previous;}
    public void acceptCommand(LivingEntity target) {
        if(state.locksRunTarget())return;
        preferred=target;combatAnchor=target.position();
        if(maid.isFallFlying()){managed=true;airborneTicks=0;pendingReturn="";startApproach();}
    }
    public void commandReturn(){if(!maid.onGround()){managed=true;beginReturn("COMMAND_COMPLETE");}}
    public boolean controlsAir() { return managed; }
    public Vec3 measuredMovement() { return movement; }
    public boolean postChargeActive() {
        return managed && SkyfarerRuntime.isSkyfarer(maid) && chargeTailUntil > maid.level().getGameTime()
            && maid.isUsingItem() && SpearCompat.isChargeWeapon(maid.getUseItem()) && !returning();
    }
    public boolean spearAttackActive() {
        return managed && SkyfarerRuntime.isSkyfarer(maid) && (state == AirCombatState.ATTACK_RUN
            || state == AirCombatState.DIVE || state == AirCombatState.SPEAR_CHARGE || state == AirCombatState.CHAIN_ATTACK || postChargeActive());
    }
    public boolean alreadyPierced(UUID target) { return passHistory.alreadyHit(target); }
    public void recordSpearHit(UUID target) {
        if (passHistory.hit(target) && SkyfarerSettings.debugLogging())
            WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} NATIVE_SPEAR_HIT target={} primary={}", maid.getUUID(), target, runTarget == null ? "none" : runTarget.getUUID());
    }
    public void acquire() {
        if (state != AirCombatState.GROUNDED || !SkyfarerRuntime.isSkyfarer(maid)) return;
        long now = maid.level().getGameTime();
        if (now < retryAt || !scanCadence.ready(now, SkyfarerSettings.ticks("scan_interval_ticks"))) return;
        String blocked = workBlockReason();
        if (!blocked.isEmpty()) { diagnostic = blocked; return; }
        if (!maid.onGround()) { diagnostic = "WAITING_FOR_GROUND"; return; }
        if (!SkyfarerEquipment.spear(maid)) { diagnostic = "MISSING_CHARGE_SPEAR"; return; }
        if (!SkyfarerEquipment.elytra(maid)) { diagnostic = "MISSING_USABLE_ELYTRA"; return; }
        if (!SkyfarerEquipment.fireworks(maid)) { diagnostic = "MISSING_FIREWORKS"; return; }
        transition(AirCombatState.TARGET_ACQUIRE, "SCANNING");
    }
    private String workBlockReason() {
        var owner = maid.getOwner();
        if (owner == null) return "OWNER_UNAVAILABLE";
        if (!owner.isAlive() || owner.isSpectator()) return "OWNER_INACTIVE";
        if (owner.level() != maid.level()) return "OWNER_OTHER_DIMENSION";
        if (maid.isNoAi()) return "AI_DISABLED";
        if (maid.guiOpening) return "MAID_GUI_OPEN";
        if (maid.isMaidInSittingPose()) return "SITTING";
        if (maid.isSleeping()) return "SLEEPING";
        if (maid.isPassenger()) return "RIDING";
        if (maid.isLeashed()) return "LEASHED";
        return "";
    }
    public void tick() {
        heading.beginTick(maid);
        corridor = new CorridorProbe(maid); tickPrediction = null; flightPlan = null; flightRequest = null;
        movement = maid.position().subtract(lastPosition); lastPosition = maid.position();
        if(state==AirCombatState.ATTACK_EXIT) exitTravelled+=movement.length();
        if(state==AirCombatState.CHAIN_ATTACK) chainTravel+=movement.length();
        sampleTargetMotion();
        stateTicks++;
        if (!maid.isAlive() || maid.isRemoved()) { release(); return; }
        if (!managed && !SkyfarerRuntime.isSkyfarer(maid)) { release(); return; }
        if(maid.isInWaterOrBubble() && state!=AirCombatState.WATER_ESCAPE && (managed || maid.level().getGameTime()>=retryAt)
            && SkyfarerRuntime.isSkyfarer(maid) && workBlockReason().isEmpty() && SkyfarerEquipment.elytra(maid)
            && (SkyfarerEquipment.fireworks(maid) || fireworks.active())) beginWaterEscape();
        if(state==AirCombatState.WATER_ESCAPE) {
            TlmFollowCompat.clearGroundMovement(maid);
            waterEscape(); logDiagnostic(); return;
        }
        acquire();
        if (managed) {
            if (chargeTailUntil >= 0 && !postChargeActive()) {
                endCharge();
                // Select from the remembered battle area before evaluating the return boundary.
                if (state == AirCombatState.BREAKAWAY || state == AirCombatState.ATTACK_EXIT) scan();
            }
            TlmFollowCompat.clearGroundMovement(maid); airborneTicks++;
            if (maid.isFallFlying()) maid.setPose(Pose.FALL_FLYING);
            double ground = AirCollisionChecker.groundBelow(maid, maid.position().add(0, 0.3, 0), 48);
            clearance = Double.isFinite(ground) ? maid.getY() - ground : Double.NaN;
            if (state != AirCombatState.TAKEOFF_PREP && state != AirCombatState.TAKEOFF && maid.onGround()) {
                diagnostic = returning() ? "LANDED" : "GROUND_CONTACT_RECOVER"; release(); logDiagnostic(); return;
            }
            String blocked = workBlockReason();
            if (!returning() && (!SkyfarerRuntime.isSkyfarer(maid) || !blocked.isEmpty() || !SkyfarerEquipment.elytra(maid)
                || !SkyfarerEquipment.spear(maid)))
                beginReturn(!SkyfarerRuntime.isSkyfarer(maid) ? "TASK_CHANGED" : !blocked.isEmpty() ? blocked : "FLIGHT_LIMIT_OR_EQUIPMENT");
            if (!returning() && (airborneTicks > SkyfarerSettings.ticks("max_airborne_ticks")
                || (!SkyfarerEquipment.fireworks(maid) && !fireworks.active()))) {
                pendingReturn = "FLIGHT_LIMIT_OR_FUEL";
                if (!AttackPolicy.finishCycle(state)) beginReturn(pendingReturn);
            }
            LivingEntity combatTarget = TargetEvaluator.attackable(maid,runTarget) ? runTarget : preferred;
            boolean hasTarget = TargetEvaluator.attackable(maid, combatTarget);
            if (!returning() && AttackPolicy.emergency(maid.position().distanceTo(anchor()),
                hasTarget ? maid.distanceTo(combatTarget) : 0, hasTarget,
                Math.max(SkyfarerSettings.emergencyTeleportRange(),TargetEvaluator.hardRange(maid)+16)))
                beginReturn("COMBAT_AREA_EMERGENCY_RANGE");
            if (state != AirCombatState.TAKEOFF_PREP && state != AirCombatState.TAKEOFF && !maid.isFallFlying() && !returning()) beginReturn("GLIDE_INTERRUPTED");
        }
        switch (state) {
            case GROUNDED -> { }
            case TARGET_ACQUIRE -> {
                scan(); transition(preferred == null ? AirCombatState.GROUNDED : AirCombatState.TARGET_SELECTED, preferred == null ? "NO_LEGAL_TARGET" : "TARGET_SELECTED");
            }
            case TARGET_SELECTED -> transition(AirCombatState.TAKEOFF_PREP, "PREPARING_JUMP");
            case TAKEOFF_PREP -> prepare();
            case TAKEOFF -> {
                launchFlight();
            }
            case WATER_ESCAPE -> { }
            case CLIMB -> {
                if(launchControlTicks>0 && surfaceLaunch!=null) {
                    launchDeparture(); break;
                }
                guide("CLIMB", waypoint, waypoint.y, false, 0, 0, Double.NaN);
                manageEnergy();
                if ((stateTicks>=8 && maid.getY() >= waypoint.y - SkyfarerSettings.get("waypoint_tolerance")) || stateTicks > 100) startApproach();
            }
            case APPROACH, REPOSITION -> approach();
            case ATTACK_RUN, DIVE, SPEAR_CHARGE -> attack();
            case CHAIN_ATTACK -> chainAttack();
            case BREAKAWAY -> breakaway();
            case ATTACK_EXIT -> attackExit();
            case LOITER_SEARCH -> {
                if (scanCadence.ready(maid.level().getGameTime(), SkyfarerSettings.ticks("scan_interval_ticks"))) scan();
                if (preferred != null) { startApproach(); break; }
                if(Double.isFinite(clearance) && clearance<6 || maid.getDeltaMovement().horizontalDistance()<.6) {
                    beginReturn("NO_TARGET_AFTER_LOITER"); break;
                }
                steer("LOITER", arcPoint(), false, false); manageEnergy();
                if (stateTicks >= SkyfarerSettings.ticks("loiter_time_ticks")) beginReturn("NO_TARGET_AFTER_LOITER");
            }
            case ABORT -> beginReturn(diagnostic);
            case RETURN_TO_OWNER, LANDING, EMERGENCY -> returnToAnchor();
        }
        if (managed && maid.isFallFlying() && !returning() && state != AirCombatState.TAKEOFF && launchControlTicks==0) terrainSafety();
        logDiagnostic();
    }
    private void prepare() {
        if (stateTicks == 0) return;
        if (!TargetEvaluator.valid(maid, preferred, false, anchor()) || maid.isInWaterOrBubble()) {
            diagnostic="TARGET_OR_LAUNCH_INVALID"; release(); return;
        }
        if(stateTicks==1) {
            // The jump still needs standing room until vanilla actually leaves the ground.
            if(!corridor.segment(maid.getEyePosition().add(0,.01,0),maid.getEyePosition().add(0,.45,0),.02,0)) {
                diagnostic="JUMP_BODY_BLOCKED"; release(); return;
            }
            var v=maid.getDeltaMovement();
            var start=new FlightPrediction.State(maid.getX(),maid.getY()+.82,maid.getZ(),v.x,.33,v.z,maid.getYRot(),maid.getXRot());
            var launchSpace=new CorridorProbe(maid,maid.getBbWidth(),.6,.4);
            Vec3 toward=preferred.position().subtract(maid.position());
            double yaw=Math.toDegrees(Math.atan2(-toward.x,toward.z));
            surfaceLaunch=selectSurfaceLaunch(start,yaw,start.y()+6,launchSpace,SurfaceLaunchPlanner.DRY);
            if(surfaceLaunch==null) { diagnostic="NO_LAUNCH_CORRIDOR"; release(); return; }
            double radians=Math.toRadians(surfaceLaunch.yaw());
            waypoint=new Vec3(maid.getX()-Math.sin(radians)*12,surfaceLaunch.height(),maid.getZ()+Math.cos(radians)*12);
        }
        heading.steerAngles(maid,surfaceLaunch.yaw(),surfaceLaunch.pitch(),180,90);
        route=new AirRoute(List.of(new AirRoute.Waypoint("LAUNCH_CORRIDOR",waypoint)));
        if (stateTicks >= 4) { maid.getJumpControl().jump(); transition(AirCombatState.TAKEOFF, "CORRIDOR_JUMP"); }
    }
    private void launchFlight() {
        if(surfaceLaunch==null) { diagnostic="NO_LAUNCH_PLAN"; release(); return; }
        if(!maid.onGround() && ElytraFlightController.startGliding(maid)) {
            // Recheck the actual jump, including residual ice momentum and snow-layer height.
            var start=predictionStart();
            surfaceLaunch=selectSurfaceLaunch(start,surfaceLaunch.yaw(),Math.max(surfaceLaunch.height(),start.y()+3),corridor,SurfaceLaunchPlanner.DRY);
            if(surfaceLaunch!=null) {
                heading.steerAngles(maid,surfaceLaunch.yaw(),surfaceLaunch.pitch(),180,90);
                if(fireworks.boost(maid,surfacePulseCost(start,surfaceLaunch,corridor,SurfaceLaunchPlanner.DRY))) {
                    launchControlTicks=SurfaceLaunchPlanner.DEPARTURE_TICKS-1;
                    transition(AirCombatState.CLIMB,"SURFACE_LAUNCH_ROCKET");
                }
            }
        }
        if(state==AirCombatState.TAKEOFF && stateTicks>SkyfarerSettings.ticks("takeoff_timeout_ticks")) beginReturn("TAKEOFF_TIMEOUT");
    }
    private SurfaceLaunchPlanner.Plan selectSurfaceLaunch(FlightPrediction.State start,double yaw,double height,
                                                         CorridorProbe space,SurfaceLaunchPlanner.Water water) {
        SurfaceLaunchPlanner.Plan best=null;
        var inventory=maid.getAvailableInv(false);
        var seen=new java.util.HashSet<Integer>();
        for(int slot=0;slot<inventory.getSlots();slot++) {
            var stack=inventory.getStackInSlot(slot);
            if(!stack.is(net.minecraft.world.item.Items.FIREWORK_ROCKET) || inventory.extractItem(slot,1,true).isEmpty()) continue;
            var data=stack.get(net.minecraft.core.component.DataComponents.FIREWORKS);
            int duration=data==null?0:data.flightDuration();
            if(!seen.add(duration)) continue;
            var plan=SurfaceLaunchPlanner.choose(start,yaw,height,gravity(),duration,space,water);
            if(plan!=null && (best==null || plan.cost()<best.cost())) best=plan;
        }
        return best;
    }
    private java.util.function.IntToDoubleFunction surfacePulseCost(FlightPrediction.State start,SurfaceLaunchPlanner.Plan plan,
                                                                  CorridorProbe space,SurfaceLaunchPlanner.Water water) {
        // Inventory selection must validate the chosen attitude, not another candidate heading.
        return duration -> SurfaceLaunchPlanner.pulseCost(start,plan,gravity(),duration,space,water);
    }
    private void launchDeparture() {
        var start=predictionStart();
        int elapsed=SurfaceLaunchPlanner.DEPARTURE_TICKS-launchControlTicks;
        double pitch=SurfaceLaunchPlanner.command(start,surfaceLaunch,elapsed,gravity(),fireworks.active());
        var next=SurfaceLaunchPlanner.step(start,surfaceLaunch.yaw(),pitch,gravity(),fireworks.active(),false);
        if(!corridor.clear(start,next,.04,.01)) {
            launchControlTicks=0; surfaceLaunch=null;
            // A changed block or external knockback uses normal emergency planning immediately.
            guide("LAUNCH_RECOVERY",waypoint,waypoint.y,false,0,0,Double.NaN); return;
        }
        heading.steerAngles(maid,surfaceLaunch.yaw(),pitch,180,90);
        launchControlTicks--;
    }
    private void beginWaterEscape() {
        compactGoal=null; chainCandidate=null; chainTarget=null;
        waterReturnReason=returning()?returnReason:"";
        if(!managed) { airborneTicks=0; pendingReturn=""; }
        endCharge(); runTarget=null; surfaceLaunch=null; launchControlTicks=0;
        previousWantToSwim=maid.getSwimManager().wantToSwim();
        maid.getSwimManager().setWantToSwim(true);
        managed=true;
        transition(AirCombatState.WATER_ESCAPE,"WATER_ESCAPE_BEGIN");
    }
    private boolean predictedWater(FlightPrediction.State s) {
        var pos=BlockPos.containing(s.x(),s.y()-.35,s.z());
        return maid.level().hasChunkAt(pos) && maid.level().getFluidState(pos).is(FluidTags.WATER);
    }
    private void waterEscape() {
        if(!SkyfarerRuntime.isSkyfarer(maid) || !workBlockReason().isEmpty() || !SkyfarerEquipment.elytra(maid)) {
            diagnostic="WATER_ESCAPE_CANCELLED"; release(); return;
        }
        if(stateTicks>Math.max(80,SkyfarerSettings.ticks("takeoff_timeout_ticks")*2)) {
            diagnostic="WATER_ESCAPE_TIMEOUT_SWIM_FALLBACK"; release(); return;
        }
        if(!maid.isInWaterOrBubble()) {
            maid.getSwimManager().setWantToSwim(previousWantToSwim);
            if(maid.onGround()) { diagnostic="WATER_ESCAPE_LANDED"; release(); return; }
            if(ElytraFlightController.startGliding(maid)) {
                surfaceLaunch=null; launchControlTicks=0;
                if(!waterReturnReason.isEmpty()) beginReturn(waterReturnReason);
                else { combatAnchor=CombatAnchor.current(maid); scan(); startApproach(); }
            }
            return;
        }
        if(maid.onGround()) maid.getJumpControl().jump();
        if(!ElytraFlightController.startGliding(maid,true)) { diagnostic="WATER_ESCAPE_LEAVING_FLOOR"; return; }
        // Only recovery permits water. Lava, blocks, borders and unloaded chunks remain blocked.
        corridor=new CorridorProbe(maid,maid.getBbWidth(),.6,.4,true); tickPrediction=null;
        var start=predictionStart();
        double height=start.y()+4;
        for(int up=0;up<32;up++) {
            var pos=BlockPos.containing(maid.getX(),maid.getY()+up,maid.getZ());
            if(!maid.level().hasChunkAt(pos)) break;
            var fluid=maid.level().getFluidState(pos);
            if(!fluid.is(FluidTags.WATER)) { height=pos.getY()+4; break; }
        }
        SurfaceLaunchPlanner.Water water=this::predictedWater;
        if(surfaceLaunch==null) {
            surfaceLaunch=fireworks.active()
                ?SurfaceLaunchPlanner.chooseRemaining(start,maid.getYRot(),height,gravity(),fireworks.remainingBoostTicks(),corridor,water)
                :selectSurfaceLaunch(start,maid.getYRot(),height,corridor,water);
            launchControlTicks=SurfaceLaunchPlanner.DEPARTURE_TICKS;
        }
        if(surfaceLaunch==null) { diagnostic="WATER_ESCAPE_NO_CORRIDOR_SWIM_FALLBACK"; release(); return; }
        double pitch=SurfaceLaunchPlanner.command(start,surfaceLaunch,SurfaceLaunchPlanner.DEPARTURE_TICKS-launchControlTicks,gravity(),true);
        var next=SurfaceLaunchPlanner.step(start,surfaceLaunch.yaw(),pitch,gravity(),true,true);
        if(!corridor.clear(start,next,.04,.01)) { diagnostic="WATER_ESCAPE_PATH_CHANGED"; release(); return; }
        heading.steerAngles(maid,surfaceLaunch.yaw(),pitch,180,90);
        if(!fireworks.active() && !fireworks.boost(maid,surfacePulseCost(start,surfaceLaunch,corridor,water))) {
            diagnostic="WATER_ESCAPE_WAITING_ROCKET";
        } else { diagnostic="WATER_ESCAPE_ROCKET"; launchControlTicks=Math.max(0,launchControlTicks-1); }
        waypoint=new Vec3(maid.getX(),height,maid.getZ());
        route=new AirRoute(List.of(new AirRoute.Waypoint("WATER_ESCAPE",waypoint)));
    }
    private void scan() {
        if (!managed) combatAnchor = CombatAnchor.current(maid);
        var scan = TargetEvaluator.scan(maid, preferred, previous, anchor(), passHistory.avoided((int)SkyfarerSettings.get("max_failed_passes")));
        preferred = scan.selection().target(); score = scan.selection().score();
        lastScan = "nearby=" + scan.scanned() + ", in_range=" + scan.withinSearch() + ", valid=" + scan.valid()
            + ", protected_radius=" + TargetEvaluator.protectedRadius(maid);
    }
    private void startApproach() {
        compactGoal=null; chainCandidate=null; chainTarget=null; chainCenter=null;
        runTarget = null;
        if(!postChargeActive()) endCharge();
        secondaryTarget = null; multiDirection = Vec3.ZERO;
        laneExit=null; runHeightOffset=0; recoveryOrigin=null;
        slopeEntry=null;debugEntry=null;debugEntryDirection=null; runFlare=SkyfarerSettings.get("level_run_distance");
        if (!TargetEvaluator.valid(maid, preferred, true, anchor())) scan();
        setupTicks = 0; setupAttempts = 0;
        if (preferred == null) transition(AirCombatState.LOITER_SEARCH, "SEARCHING_AFTER_PASS");
        else {
            Vec3 incoming = HeadingController.horizontal(preferred.position().subtract(maid.position()));
            secondaryTarget = TargetEvaluator.secondary(maid, preferred, incoming, anchor());
            if (secondaryTarget != null) {
                Vec3 offset = secondaryTarget.position().subtract(preferred.position());
                multiDirection = offset.horizontalDistanceSqr() < 0.01 ? incoming : HeadingController.horizontal(offset);
                if (multiDirection.dot(incoming) < 0) multiDirection = multiDirection.scale(-1);
            }
            transition(AirCombatState.APPROACH, "ASSESS_NEXT_ATTACK_ENTRY");
        }
    }
    private Vec3 anchor() { return combatAnchor == null ? CombatAnchor.current(maid) : combatAnchor; }
    private double attackHeight(LivingEntity target) {
        double minimum = target.getY() + SkyfarerSettings.get("attack_ground_clearance") + maid.getEyeHeight();
        return Math.max(minimum + 0.15, TargetEvaluator.aimPoint(target).y);
    }
    private void beginReposition(String reason) {
        if(!postChargeActive()) endCharge();
        runTarget = null;
        setupAttempts++;
        double height=attackHeight(preferred)+(flatTerrain(sampleSlope(preferred))?9:SkyfarerSettings.get("approach_height"));
        double speed=Math.max(.7,Math.min(1.2,maid.getDeltaMovement().horizontalDistance()));
        double entry=Math.clamp(Math.max(AttackPolicy.minimumEntry(speed,SpearCompat.delayTicks(maid.getMainHandItem()),SpearCompat.mobReach(maid.getMainHandItem())),
            DiveProfile.minimumDistance(height-attackHeight(preferred),speed,gravity()))+10,18,Math.min(34,SkyfarerSettings.get("reposition_distance")+6));
        Vec3 radial=HeadingController.horizontal(maid.position().subtract(preferred.position()));
        Vec3 staging=preferred.position().add(radial.scale(entry));
        compactGoal=new CompactTurnPlanner.Goal(staging.x,height,staging.z,maid.getX(),maid.getZ(),
            Math.max(32,Math.min(48,Math.max(staging.subtract(maid.position()).horizontalDistance(),preferred.position().subtract(maid.position()).horizontalDistance())+12)),preferred.getX(),preferred.getZ());
        transition(AirCombatState.REPOSITION, reason);
        // A same-tick exit -> approach -> reposition handoff must not keep the old exit plan.
        flyCompactTurn();
    }
    private Vec3 forward() {
        return HeadingController.horizontal(maid.getDeltaMovement().horizontalDistanceSqr() > 0.04 ? maid.getDeltaMovement() : maid.getLookAngle());
    }
    /** A local turn consumes momentum, not another long outward leg. */
    private void compactTurn() {
        if(compactGoal==null) { transition(AirCombatState.APPROACH,"TURN_REASSESS"); return; }
        if(compactGoal.readyInward(maid.getX(),maid.getZ())) compactGoal=compactGoal.inward();
        flyCompactTurn();
        double angle=CompactTurnPlanner.angle(predictionStart(),preferred.getX(),preferred.getZ());
        double speed=approachSpeed(),distance=preferred.position().subtract(maid.position()).horizontalDistance();
        double entry=AttackPolicy.minimumEntry(speed,SpearCompat.delayTicks(maid.getMainHandItem()),SpearCompat.mobReach(maid.getMainHandItem()));
        if(flatTerrain(sampleSlope(preferred))) entry=Math.max(entry,DiveProfile.minimumDistance(maid.getEyeY()-attackHeight(preferred),speed,gravity()));
        // A usable entry can appear before reaching the nominal staging point. Take that window;
        // completing the staging orbit is not itself an objective.
        if((angle<18 && distance>=entry) || stateTicks>=100) {
            compactGoal=null;
            transition(AirCombatState.APPROACH,angle<22?"COMPACT_TURN_ALIGNED":"COMPACT_TURN_REASSESS");
            approach();
        }
    }
    private void flyCompactTurn() {
        var start=predictionStart(); var space=navigationSpace();
        double rate=Math.max(5,SkyfarerSettings.turnRate());
        var plan=CompactTurnPlanner.choose(start,compactGoal,fireworks.remainingBoostTicks(),gravity(),rate,space);
        double speed=Math.hypot(start.vx(),start.vz());
        // A short climb normally needs no new thrust. Only an actual low-energy descent can request it.
        if(state!=AirCombatState.LANDING && !fireworks.active() && speed<.75 && start.vy()<-.03
            && Double.isFinite(clearance) && clearance<8 && start.y()<compactGoal.y()+2) {
            if(fireworks.boost(maid,duration->CompactTurnPlanner.pulseCost(start,compactGoal,duration,gravity(),rate,space))) {
                plan=CompactTurnPlanner.choose(start,compactGoal,fireworks.remainingBoostTicks(),gravity(),rate,space);
                diagnostic="COMPACT_CLIMB_PULSE";
            }
        }
        compactPlan=plan;
        if(plan==null) {
            var destination=new Vec3(compactGoal.x(),compactGoal.y(),compactGoal.z());
            flightRequest=request(destination,destination.y,false,0,0,null,true);
            flightPlan=GlidePlanner.choose(start,flightRequest,space); applyPlan();
        } else {
            var c=plan.command();
            flightPlan=new GlidePlanner.Plan(c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),GlidePlanner.Mode.RECOVERY,true,plan.cost(),0,32,0);
            applyPlan();
        }
        waypoint=new Vec3(compactGoal.x(),compactGoal.y(),compactGoal.z());
        route=new AirRoute(List.of(new AirRoute.Waypoint("CLIMB_BRAKE_TURN",maid.getEyePosition().add(heading.desired().scale(8))),
            new AirRoute.Waypoint("TURN_DESTINATION",waypoint)));
    }
    /** Never fund a pulse which carries recovery further out of the active battle area. */
    private GlidePlanner.Corridor navigationSpace() {
        Vec3 center=anchor();
        double radius=Math.max(TargetEvaluator.hardRange(maid)+12,maid.position().subtract(center).horizontalDistance()+6);
        return (a,b,side,vertical)->Math.hypot(b.x()-center.x,b.z()-center.z)<=radius && corridor.clear(a,b,side,vertical);
    }
    /** Moving arc waypoint; actual velocity turns only through vanilla flight physics. */
    private Vec3 arcPoint() {
        Vec3 forward = forward();
        Vec3 to = preferred == null ? anchor().subtract(maid.position()) : preferred.position().subtract(maid.position());
        double sign = forward.x * to.z - forward.z * to.x >= 0 ? 1 : -1;
        double radius = Math.clamp(maid.getDeltaMovement().horizontalDistance() / Math.toRadians(SkyfarerSettings.turnRate()), 8, 28);
        Vec3 point = maid.getEyePosition().add(forward.scale(radius)).add(-forward.z * radius * 0.7 * sign, 0, forward.x * radius * 0.7 * sign);
        double y = preferred == null ? maid.getEyeY() : preferred.getY() + Math.min(8, SkyfarerSettings.climbHeight());
        return new Vec3(point.x, y, point.z);
    }
    private void approach() {
        setupTicks++;
        if (!TargetEvaluator.valid(maid, preferred, true, anchor())) { scan(); startApproach(); return; }
        if (setupTicks > SkyfarerSettings.ticks("attack_timeout_ticks") * 4) { retryAnotherTarget("ALIGNMENT_RESELECT"); return; }
        if (state == AirCombatState.REPOSITION) {
            compactTurn(); return;
        }
        Vec3 target = TargetEvaluator.aimPoint(preferred), offset = target.subtract(maid.getEyePosition());
        double distance = offset.horizontalDistance();
        double velocityAngle = HeadingController.horizontalAngle(forward(), offset);
        double lookAngle = HeadingController.horizontalAngle(maid.getLookAngle(), offset);
        double angle = Math.max(velocityAngle, lookAngle);
        double height = attackHeight(preferred);
        double speed = approachSpeed();
        int windup = SpearCompat.delayTicks(maid.getMainHandItem());
        double reach = SpearCompat.mobReach(maid.getMainHandItem());
        double minimumEntry = AttackPolicy.minimumEntry(speed, windup, reach);
        if(flatTerrain(sampleSlope(preferred)) && maid.getEyeY()-height>=3)
            minimumEntry=Math.max(minimumEntry,DiveProfile.minimumDistance(maid.getEyeY()-height,speed,gravity()));
        boolean planned=false;
        double entryDistance = Math.max(SkyfarerSettings.attackEntryDistance() + 24, minimumEntry + 14);
        if(distance<=entryDistance && distance>=minimumEntry && angle<25)
            planned=planAttackLane(preferred,HeadingController.horizontal(offset));
        boolean stable = maid.getDeltaMovement().y >= -0.45 && maid.getDeltaMovement().y <= 0.5
            && Math.abs(maid.getXRot()) < 30 && maid.getEyeY() >= height - 0.2
            && maid.getEyeY() <= height + SkyfarerSettings.get("approach_height") + 3;
        if(planned && runFlare<0) {
            double expected=FlightPrediction.pathHeight(distance,height+runHeightOffset,runSlope,runFlare);
            double expectedVy=FlightPrediction.pathVerticalSpeed(predictionStart(),distance,runSlope,runFlare);
            stable=Math.abs(maid.getEyeY()-expected)<2.5 && Math.abs(maid.getDeltaMovement().y-expectedVy)<1.3 && Math.abs(maid.getXRot())<80;
        }
        if (distance <= entryDistance && stable && AttackPolicy.canEnter(distance, speed, windup, reach,
            lookAngle, velocityAngle, SkyfarerSettings.attackTurnRate(), SkyfarerSettings.maxTurnAngle())) {
            if (!planned && !planAttackLane(preferred, HeadingController.horizontal(offset))) {
                beginReposition("ATTACK_CORRIDOR_BLOCKED"); return;
            }
            // A new pass has a fresh use session and native windup, even if the previous tail is still held.
            endCharge();
            runTarget = preferred; runOrigin = target; runDirection = HeadingController.horizontal(offset); runTicks = 0;
            passHistory.begin(runTarget.getUUID());
            chainCenter=runTarget.position(); chainCandidate=null; chainHits=0; chainTicks=0; chainTravel=0;
            if(runFlare>=0) runSlope = Math.max(0, maid.getEyeY() - height - runHeightOffset) / Math.max(6, distance - SkyfarerSettings.get("level_run_distance"));
            transition(AirCombatState.ATTACK_RUN, "RUN_TARGET_LOCKED"); attack(); return;
        }
        if (distance < minimumEntry) { beginReposition(stable ? "TOO_CLOSE_EXTEND_AGAIN" : "UNSTABLE_ENTRY_EXTEND_AGAIN"); return; }
        if(angle>40) { beginReposition("CLIMB_BRAKE_TURN_IN"); return; }
        if(planned && runFlare==-1 && slopeEntry!=null) {
            guide("MERGE_SLOPE_LINE",slopeEntry,slopeEntry.y,false,0,0,Double.NaN); manageEnergy(); return;
        }
        Vec3 entry = target;
        if (TargetEvaluator.attackable(maid, secondaryTarget) && distance > minimumEntry + 12)
            entry = target.subtract(multiDirection.scale(Math.min(minimumEntry, distance * 0.35)));
        double approachHeight=flatTerrain(sampleSlope(preferred))?Math.max(9,SkyfarerSettings.get("approach_height")):SkyfarerSettings.get("approach_height");
        guide(entry == target ? "TURN_IN" : "MULTI_PIERCE_ENTRY", entry, height + approachHeight, false, 0, 0, Double.NaN);
        manageEnergy();
    }
    private void attack() {
        runTicks++;
        if(passHistory.primaryHit()) { beginBreakaway("PRIMARY_HIT"); return; }
        // Range is reconsidered after recovery. Moving the owner cannot invalidate a locked pass.
        if (!TargetEvaluator.attackable(maid, runTarget)) { beginBreakaway("RUN_TARGET_INVALID"); return; }
        Vec3 target = TargetEvaluator.aimPoint(runTarget), toTarget = target.subtract(maid.getEyePosition());
        double along = toTarget.dot(runDirection);
        if (FlightMath.passed(along, SpearCompat.mobReach(maid.getMainHandItem()))) { previous = runTarget.getUUID(); beginBreakaway("PASSED_TARGET"); return; }
        if (runTicks > SkyfarerSettings.ticks("attack_timeout_ticks")) { beginBreakaway("RUN_TIMEOUT"); return; }
        if (target.distanceTo(runOrigin) > SkyfarerSettings.get("max_target_displacement")
            || (along > 4 && HeadingController.horizontalAngle(runDirection, toTarget) > SkyfarerSettings.diveMaxCorrectionAngle())) { beginBreakaway("RUN_CORRECTION_LIMIT"); return; }
        if (state == AirCombatState.ATTACK_RUN) transition(AirCombatState.DIVE, "DIVING_TO_INTERCEPT");
        double speed = approachSpeed();
        double closingSpeed = Math.max(0.25, speed - targetMotion.dot(runDirection));
        double leadTicks = Math.clamp((along - SpearCompat.mobReach(maid.getMainHandItem())) / closingSpeed, 0, 12);
        Vec3 intercept = target.add(targetMotion.scale(leadTicks));
        double flightY = attackHeight(runTarget) + runHeightOffset;
        // Continue correcting right up to contact; the former 3-block cutoff froze lateral misses.
        Vec3 through = along <= 0 ? maid.getEyePosition().add(runDirection.scale(12)) : intercept;
        if (runFlare!=-2 && along > 0 && TargetEvaluator.attackable(maid, secondaryTarget)) {
            Vec3 secondaryPoint = new Vec3(secondaryTarget.getX(), maid.getEyeY(), secondaryTarget.getZ());
            // Aim toward the second enemy only when the same line still crosses the primary's column.
            var primaryColumn = runTarget.getBoundingBox().inflate(SpearCompat.hitboxMargin(maid.getMainHandItem()),
                SkyfarerSettings.get("spear_vertical_expansion"), SpearCompat.hitboxMargin(maid.getMainHandItem()));
            if (primaryColumn.clip(maid.getEyePosition(), secondaryPoint).isPresent()) through = secondaryPoint;
        }
        double supportFloor = runTarget.getY() + SkyfarerSettings.get("attack_ground_clearance") + maid.getEyeHeight();
        guide(runFlare==-2?"CURVED_DIVE":runFlare==-1?"SLOPE_ATTACK":"FLIGHT_GUIDANCE", through, flightY, true, runFlare<0?along:Math.max(0, along), runSlope, supportFloor);
        manageEnergy();
        if(state==AirCombatState.REPOSITION) return;
        if (flightPlan != null && !GlidePlanner.ordinary(flightPlan)) return;
        if((runTicks==1 || runTicks%4==0) && along<Math.max(12,speed*16)) previewChain();
        Vec3 guidance = maid.getEyePosition().add(heading.desired().scale(12));
        route = new AirRoute(List.of(new AirRoute.Waypoint("FLIGHT_GUIDANCE", guidance),
            new AirRoute.Waypoint("INTERCEPT", new Vec3(intercept.x, flightY, intercept.z)),
            new AirRoute.Waypoint("BREAKAWAY", intercept.add(runDirection.scale(SkyfarerSettings.breakawayDistance())).add(0, 3, 0))));
        double chargeSpeed = Math.max(closingSpeed, fireworks.active() ? 1.7 - targetMotion.dot(runDirection) : 0);
        if (FlightMath.chargeDue(along, chargeSpeed, SpearCompat.delayTicks(maid.getMainHandItem()))) {
            if (!maid.isUsingItem()) SpearCompat.startCharge(maid);
            if (state != AirCombatState.SPEAR_CHARGE) transition(AirCombatState.SPEAR_CHARGE, "NATIVE_SPEAR_CHARGE");
        }
        if (maid.isUsingItem() && maid.getTicksUsingItem() > SpearCompat.chargeWindow(maid.getUseItem())) { beginBreakaway("CHARGE_WINDOW_EXPIRED"); return; }
    }
    private void beginBreakaway(String reason) {
        if(state!=AirCombatState.CHAIN_ATTACK && passHistory.primaryHit() && tryBeginChain()) return;
        if(runTarget!=null) previous=runTarget.getUUID();
        boolean completed = reason.equals("PASSED_TARGET") || passHistory.primaryHit();
        if (completed && maid.isUsingItem() && SkyfarerSettings.ticks("post_attack_charge_ticks") > 0) {
            chargeTailUntil = maid.level().getGameTime() + SkyfarerSettings.ticks("post_attack_charge_ticks");
        } else endCharge();
        runDirection = forward();
        recoveryOrigin = maid.getEyePosition();
        selectExit();
        transition(AirCombatState.ATTACK_EXIT, reason + ":" + exitStyle);
        attackExit();
    }
    private FlightPrediction.Aim chainAim(LivingEntity target) {
        double expansion=SkyfarerSettings.get("spear_vertical_expansion");
        var v=target.onGround()?new Vec3(target.getDeltaMovement().x,0,target.getDeltaMovement().z):target.getDeltaMovement();
        return new FlightPrediction.Aim(target.getX(),target.getY()-expansion,target.getZ(),target.getBbWidth()*.5,
            target.getBbHeight()+2*expansion,v.x,v.y,v.z,SpearCompat.mobMinReach(maid.getMainHandItem()),
            SpearCompat.mobReach(maid.getMainHandItem()),SpearCompat.hitboxMargin(maid.getMainHandItem()),0);
    }
    private ChainAttackPlanner.Plan chainPlan(FlightPrediction.State from,LivingEntity target,int remaining,int rocket) {
        if(chainCenter==null || !TargetEvaluator.valid(maid,target,true,anchor()) || !TargetEvaluator.visible(maid,target) || passHistory.alreadyHit(target.getUUID())
            || target.position().distanceTo(chainCenter)>SkyfarerSettings.get("secondary_target_radius")) return null;
        double height=Math.clamp(from.y(),attackHeight(target),attackHeight(target)+Math.min(1.5,SkyfarerSettings.get("spear_vertical_expansion")));
        return ChainAttackPlanner.choose(from,chainAim(target),height,rocket,gravity(),
            Math.max(.3,SpearCompat.minimumDamageSpeed(maid.getMainHandItem())+.05),remaining,Math.max(0,18-chainTravel),navigationSpace());
    }
    private LivingEntity selectChain(FlightPrediction.State from,int remaining,int rocket) {
        if(chainCenter==null || remaining<=0) return null;
        LivingEntity best=null; double cost=Double.POSITIVE_INFINITY;
        double radius=SkyfarerSettings.get("secondary_target_radius");
        if(radius<=0) return null;
        for(var target:maid.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(chainCenter,chainCenter).inflate(radius))) {
            if(target==runTarget) continue;
            var plan=chainPlan(from,target,remaining,rocket);
            if(plan!=null && plan.cost()<cost) { best=target; cost=plan.cost(); }
        }
        return best;
    }
    private void previewChain() {
        chainCandidate=null;
        if(chainCenter==null || SkyfarerSettings.ticks("post_attack_charge_ticks")<=0 || !pendingReturn.isEmpty() || flightRequest==null) return;
        double radius=SkyfarerSettings.get("secondary_target_radius");
        if(radius<=0 || maid.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(chainCenter,chainCenter).inflate(radius),
            target->target!=runTarget && TargetEvaluator.valid(maid,target,true,anchor()) && !passHistory.alreadyHit(target.getUUID())
                && target.position().distanceTo(chainCenter)<=radius).isEmpty()) return;
        var contact=ChainAttackPlanner.primaryContact(predictionStart(),flightRequest,corridor);
        if(contact!=null) chainCandidate=selectChain(contact,Math.min(40,SkyfarerSettings.ticks("post_attack_charge_ticks")),fireworks.remainingBoostTicks());
    }
    private boolean tryBeginChain() {
        if(chainCandidate==null || !pendingReturn.isEmpty() || !maid.isUsingItem() || SkyfarerSettings.ticks("post_attack_charge_ticks")<=0) return false;
        int budget=Math.min(40,SkyfarerSettings.ticks("post_attack_charge_ticks"));
        if(chainPlan(predictionStart(),chainCandidate,budget,fireworks.remainingBoostTicks())==null) return false;
        chainTarget=chainCandidate; chainCandidate=null; chainTicks=0; chainHits=0; chainTravel=0;
        chargeTailUntil=maid.level().getGameTime()+SkyfarerSettings.ticks("post_attack_charge_ticks");
        passHistory.settle(); previous=runTarget.getUUID();
        transition(AirCombatState.CHAIN_ATTACK,"PRIMARY_HIT_CHAIN_CONFIRMED:"+chainTarget.getUUID()); chainAttack(); return true;
    }
    private void chainAttack() {
        chainTicks++;
        int remaining=Math.min(40,SkyfarerSettings.ticks("post_attack_charge_ticks"))-chainTicks;
        if(remaining<=0 || chainTravel>=18 || !postChargeActive() || maid.getTicksUsingItem()>=SpearCompat.chargeWindow(maid.getUseItem())) {
            beginBreakaway("CHAIN_BUDGET_COMPLETE"); return;
        }
        if(chainTarget!=null && passHistory.alreadyHit(chainTarget.getUUID())) {
            chainHits++;
            chainTarget=chainHits>=2?null:selectChain(predictionStart(),remaining,fireworks.remainingBoostTicks());
            if(chainTarget!=null && SkyfarerSettings.debugLogging()) WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} CHAIN_NEXT target={} ticks={} travel={}",
                maid.getUUID(),chainTarget.getUUID(),chainTicks,chainTravel);
        }
        var plan=chainPlan(predictionStart(),chainTarget,remaining,fireworks.remainingBoostTicks());
        if(plan==null) { beginBreakaway("CHAIN_RECOVERY_REQUIRED"); return; }
        var c=plan.command();
        flightPlan=new GlidePlanner.Plan(c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),GlidePlanner.Mode.GLIDE,true,plan.cost(),0,24,0);
        applyPlan();
        route=new AirRoute(List.of(new AirRoute.Waypoint("CHAIN_TRACK",maid.getEyePosition().add(heading.desired().scale(8))),
            new AirRoute.Waypoint("CHAIN_TARGET",chainTarget.getBoundingBox().getCenter())));
        diagnostic="CHAIN_TRACK_"+(chainHits+2);
    }
    private void breakaway() {
        selectExit(); transition(AirCombatState.ATTACK_EXIT, "SELECT_ATTACK_EXIT"); attackExit();
    }
    /** Entry -> contact -> exit, using actual block shapes, never block density or a height map. */
    private boolean planAttackLane(LivingEntity target, Vec3 direction) {
        double side=SkyfarerSettings.get("corridor_side_margin"), vertical=SkyfarerSettings.get("corridor_vertical_margin");
        double base=attackHeight(target), distance=maid.position().distanceTo(target.position());
        double x=target.getX(),z=target.getZ();
        var slopes=sampleSlope(target);
        if(flatTerrain(slopes) && curvedDive(target,direction)) return true;
        AttackLinePlanner.Line bestLine=null; double bestGx=0,bestGz=0;
        for(int fit=0;fit<2;fit++) {
            double gx=fit==0?slopes.nearX():slopes.wideX(),gz=fit==0?slopes.nearZ():slopes.wideZ();
            if(fit==1 && Math.hypot(gx-slopes.nearX(),gz-slopes.nearZ())<.025) continue;
            double gradient=Math.clamp(gx*direction.x+gz*direction.z,-1.5,1.5);
            if(Math.abs(gradient)<=.06 && maid.getEyeY()>=base-.2) continue;
            double minimum=AttackPolicy.minimumEntry(approachSpeed(),
                SpearCompat.delayTicks(maid.getMainHandItem()),SpearCompat.mobReach(maid.getMainHandItem()));
            var line=AttackLinePlanner.choose(predictionStart(),x,base,z,direction.x,direction.z,gradient,-gx*direction.z+gz*direction.x,minimum,
                SkyfarerSettings.attackExitDistance(),SkyfarerSettings.get("spear_vertical_expansion"),
                SkyfarerEquipment.fireworks(maid)||fireworks.active(),
                (ax,ay,az,bx,by,bz)->corridor.segment(new Vec3(ax,ay,az),new Vec3(bx,by,bz),side,vertical));
            if(line!=null && (bestLine==null || line.cost()<bestLine.cost())) { bestLine=line; bestGx=gx; bestGz=gz; }
        }
        if(bestLine!=null) {
            var line=bestLine;
            runSlope=-line.gradient(); runFlare=-1; runHeightOffset=line.height()-base;
            laneGradientX=bestGx; laneGradientZ=bestGz;
            slopeEntry=new Vec3(line.entryX(),line.entryY(),line.entryZ());
            debugEntry=slopeEntry;debugEntryDirection=new Vec3(target.getX(),line.height(),target.getZ()).subtract(slopeEntry).normalize();
            laneExit=new Vec3(line.exitX(),line.exitY(),line.exitZ());
            laneStatus="slope="+line.gradient()+", samples="+slopes.samples()+", routeCost="+line.cost()+", continuous contact/exit"; return true;
        }
        runFlare=SkyfarerSettings.get("level_run_distance"); slopeEntry=null;
        for (double extra : new double[]{0,0.5,1,1.5,2}) {
            if (extra > SkyfarerSettings.get("spear_vertical_expansion")) break;
            Vec3 contact=new Vec3(target.getX(),base+extra,target.getZ());
            Vec3 entry=contact.subtract(direction.scale(Math.min(SkyfarerSettings.get("level_run_distance"),distance*0.7)));
            if (!corridor.segment(maid.getEyePosition(),entry,side,vertical)
                || !corridor.segment(entry,contact,side,vertical)) continue;
            for (double turn : new double[]{0,-35,35,-65,65})
                for (double rise : new double[]{0,3,6}) for (double delay : new double[]{0,3,6}) {
                Vec3 intermediate=contact.add(direction.scale(delay));
                double angle=Math.toRadians(turn);
                Vec3 outgoing=new Vec3(direction.x*Math.cos(angle)-direction.z*Math.sin(angle),0,
                    direction.x*Math.sin(angle)+direction.z*Math.cos(angle));
                Vec3 exit=intermediate.add(outgoing.scale(Math.max(0,SkyfarerSettings.attackExitDistance()-delay))).add(0,rise,0);
                exit=capExit(contact,exit,SkyfarerSettings.attackExitDistance());
                if (corridor.lane(contact,intermediate,exit,side,vertical)) {
                    runHeightOffset=extra; laneExit=exit;
                    debugEntry=maid.getEyePosition();debugEntryDirection=entry.subtract(debugEntry).normalize();
                    laneStatus="clear entry/contact/exit; offset="+extra+", rise="+rise+", turn="+turn; return true;
                }
            }
        }
        laneStatus="blocked entry/contact/exit"; return false;
    }
    private static boolean flatTerrain(SlopeSampler.Estimate slopes) {
        return slopes.samples()>=12 && Math.hypot(slopes.nearX(),slopes.nearZ())<.16 && Math.hypot(slopes.wideX(),slopes.wideZ())<.16;
    }
    private boolean curvedDive(LivingEntity target,Vec3 direction) {
        double base=attackHeight(target),distance=target.position().subtract(maid.position()).horizontalDistance();
        double rise=maid.getEyeY()-base;
        if(rise<3 || distance<DiveProfile.minimumDistance(rise,approachSpeed(),gravity()) || rise/distance>1.2) return false;
        double slope=DiveProfile.slope(rise,distance);
        Vec3 last=maid.getEyePosition();
        double side=SkyfarerSettings.get("corridor_side_margin"),vertical=SkyfarerSettings.get("corridor_vertical_margin");
        for(double d=Math.max(0,distance-1);;d=Math.max(0,d-1)) {
            Vec3 point=new Vec3(target.getX()-direction.x*d,FlightPrediction.pathHeight(d,base,slope,-2),target.getZ()-direction.z*d);
            if(!corridor.segment(last,point,side,vertical)) return false;
            last=point; if(d==0) break;
        }
        Vec3 exit=last.add(direction.scale(SkyfarerSettings.attackExitDistance()*.65)).add(0,4,0);
        if(!corridor.segment(last,exit,side,vertical)) return false;
        runFlare=-2; runSlope=slope; runHeightOffset=0; slopeEntry=null; laneExit=exit;
        debugEntry=maid.getEyePosition();double nextDistance=Math.max(0,distance-1);
        debugEntryDirection=new Vec3(target.getX()-direction.x*nextDistance,FlightPrediction.pathHeight(nextDistance,base,slope,-2),target.getZ()-direction.z*nextDistance).subtract(debugEntry).normalize();
        laneGradientX=0; laneGradientZ=0;
        laneStatus="curved dive; rise="+rise+", distance="+distance; return true;
    }
    private SlopeSampler.Estimate sampleSlope(LivingEntity target) {
        long now=maid.level().getGameTime();
        double radius=SkyfarerSettings.get("slope_sample_radius"),step=SkyfarerSettings.get("slope_sample_step");
        if(sampledSlope==null || !target.getUUID().equals(sampledTarget) || now-sampledAt>=5
            || target.position().distanceToSqr(sampledPosition)>.0625 || radius!=sampledRadius || step!=sampledStep) {
            sampledSlope=SlopeSampler.sample(target.getX(),target.getY(),target.getZ(),radius,step,corridor::supportSample);
            sampledAt=now; sampledTarget=target.getUUID(); sampledPosition=target.position(); sampledRadius=radius; sampledStep=step;
        }
        return sampledSlope;
    }
    /** Select a fixed exit, not a continuously recentered pull-up point. */
    private void selectExit() {
        Vec3 origin=maid.getEyePosition(), forward=forward();
        double recoveryY=AttackPolicy.recoveryHeight(origin.y,
            TargetEvaluator.attackable(maid,preferred)?attackHeight(preferred):origin.y, SkyfarerSettings.get("approach_height"));
        double length=AttackPolicy.exitLength(SkyfarerSettings.breakawayDistance(),approachSpeed(),SkyfarerSettings.turnRate());
        exitDistanceLimit=length; exitTravelled=0;
        Vec3 shortLane=laneExit==null?null:capExit(origin,laneExit,length);
        Vec3 selected=null; String style="STRAIGHT_CLIMB";
        boolean pierce=postChargeActive() && TargetEvaluator.attackable(maid,secondaryTarget)
            && secondaryTarget.position().subtract(origin).dot(forward)>0;
        var template=request(origin.add(forward.scale(length)),recoveryY,false,0,0,null,true);
        if(runFlare==-2 || state==AirCombatState.CHAIN_ATTACK) {
            Vec3 climb=capExit(origin,origin.add(forward.scale(Math.min(5,length))).add(0,4,0),length);
            var planned=request(climb,climb.y,false,0,0,null,true);
            if(corridor.segment(origin,climb,.04,.04) && GlidePlanner.ordinary(GlidePlanner.choose(predictionStart(),planned,corridor))) {
                waypoint=climb; exitStyle="POST_DIVE_CLIMB"; recoveryOrigin=origin; return;
            }
        }
        // Keep the exit used to validate the strike if it remains dynamically reachable.
        if(runFlare==-1 && shortLane!=null && HeadingController.horizontalAngle(forward,shortLane.subtract(origin))<135 && origin.distanceTo(shortLane)>3
            && corridor.segment(origin,shortLane,.04,.04)) {
            var planned=request(shortLane,shortLane.y,false,0,0,null,true);
            if(GlidePlanner.ordinary(GlidePlanner.choose(predictionStart(),planned,corridor))) {
                waypoint=shortLane; exitStyle="SLOPE_CORRIDOR_EXIT"; recoveryOrigin=origin; return;
            }
        }
        boolean target=TargetEvaluator.attackable(maid,preferred);
        var choice=AttackExitPlanner.choose(predictionStart(),template,recoveryY,length,pierce,
            target?preferred.getX():Double.NaN,target?preferred.getZ():Double.NaN,corridor,
            (x,y,z)->corridor.segment(origin,new Vec3(x,y,z),template.sideMargin(),template.verticalMargin()));
        if (choice!=null) { selected=new Vec3(choice.x(),choice.y(),choice.z()); style=choice.style().name(); }
        // A roof can require continuing through the validated flat exit before climbing outside it.
        if (selected==null && shortLane!=null && shortLane.subtract(origin).dot(forward)>0
            && corridor.segment(origin,shortLane,0.04,0.04)) { selected=shortLane; style="CORRIDOR_EXIT"; }
        waypoint=selected==null?capExit(origin,origin.add(forward.scale(length)).add(0,3,0),length):selected;
        exitStyle=style; recoveryOrigin=origin;
    }
    private static Vec3 capExit(Vec3 origin,Vec3 point,double maximum) {
        var capped=AttackExitPlanner.cap(origin.x,origin.y,origin.z,point.x,point.y,point.z,maximum);
        return new Vec3(capped.x(),capped.y(),capped.z());
    }
    private void attackExit() {
        if (recoveryOrigin==null) selectExit();
        Vec3 destination=waypoint;
        guide("ATTACK_EXIT_"+exitStyle,destination,destination.y,false,0,0,Double.NaN); manageEnergy();
        if(state!=AirCombatState.ATTACK_EXIT) return;
        boolean reached=maid.getEyePosition().distanceTo(destination)<Math.max(4,approachSpeed()*4)
            || maid.getEyePosition().subtract(destination).dot(destination.subtract(recoveryOrigin))>=0;
        if (reached || exitTravelled>=exitDistanceLimit || AttackPolicy.exitComplete(stateTicks,false,exitTravelled,exitDistanceLimit,
            flightPlan!=null && GlidePlanner.ordinary(flightPlan))) {
            passHistory.settle();
            runTarget=null;
            if (!pendingReturn.isEmpty()) { beginReturn(pendingReturn); return; }
            combatAnchor=CombatAnchor.current(maid); scan();
            // The completed exit point may now be behind us. Validate the next route immediately,
            // rather than orbiting that obsolete point while waiting for its forecast to become safe.
            if(preferred==null) { beginReturn("NO_TARGET_AFTER_LOITER"); returnToAnchor(); }
            else { startApproach(); approach(); }
        }
    }
    private void terrainSafety() {
        if (flightPlan==null || GlidePlanner.ordinary(flightPlan)) return;
        endCharge();
        if (!returning()) {
            runTarget=null;
            if (state!=AirCombatState.ATTACK_EXIT) {
                recoveryOrigin=maid.getEyePosition();
                exitDistanceLimit=AttackPolicy.exitLength(SkyfarerSettings.breakawayDistance(),approachSpeed(),SkyfarerSettings.turnRate());
                exitTravelled=0;
                waypoint=recoveryOrigin.add(heading.desired().scale(Math.min(8,exitDistanceLimit)));
                transition(AirCombatState.ATTACK_EXIT,"CORRIDOR_"+flightPlan.mode());
            }
        }
        diagnostic="CORRIDOR_"+flightPlan.mode();
    }
    private double gravity() { return maid.getAttributeValue(Attributes.GRAVITY); }
    private FlightPrediction.State predictionState() {
        Vec3 v = maid.getDeltaMovement();
        return new FlightPrediction.State(maid.getX(), maid.getEyeY(), maid.getZ(), v.x, v.y, v.z, maid.getYRot(), maid.getXRot());
    }
    private void guide(String name, Vec3 point, double levelHeight, boolean attack, double distance, double slope, double minimumEyeY) {
        FlightPrediction.Aim aim = null;
        if (attack && runTarget != null && distance > 0) {
            Vec3 targetVelocity = targetMotion;
            int readyIn = Math.max(0, SpearCompat.delayTicks(maid.getMainHandItem()) - (maid.isUsingItem() ? maid.getTicksUsingItem() : 0));
            double expansion = SkyfarerSettings.get("spear_vertical_expansion");
            aim = new FlightPrediction.Aim(runTarget.getX(), runTarget.getY() - expansion, runTarget.getZ(), runTarget.getBbWidth() * 0.5,
                runTarget.getBbHeight() + expansion * 2, targetVelocity.x, targetVelocity.y, targetVelocity.z,
                SpearCompat.mobMinReach(maid.getMainHandItem()), SpearCompat.mobReach(maid.getMainHandItem()),
                SpearCompat.hitboxMargin(maid.getMainHandItem()), readyIn);
        }
        flightRequest=request(point,levelHeight,attack,distance,slope,aim,state==AirCombatState.ATTACK_EXIT);
        if(attack && laneExit!=null) flightRequest=flightRequest.withExit(runFlare==-1
            ?new GlidePlanner.Exit(laneExit.x,laneExit.y,laneExit.z,laneGradientX,laneGradientZ)
            :new GlidePlanner.Exit(laneExit.x,laneExit.y,laneExit.z));
        flightPlan=GlidePlanner.choose(predictionStart(),flightRequest,corridor);
        applyPlan();
        Vec3 guidance=maid.getEyePosition().add(heading.desired().scale(12));
        waypoint = new Vec3(point.x, levelHeight, point.z);
        route = new AirRoute(List.of(new AirRoute.Waypoint(name, guidance), new AirRoute.Waypoint("PLANNED_POINT", waypoint)));
    }
    private FlightPrediction.State predictionStart() {
        if (tickPrediction==null) tickPrediction=predictionState();
        return tickPrediction;
    }
    private GlidePlanner.Request request(Vec3 point,double height,boolean attack,double distance,double slope,FlightPrediction.Aim aim,boolean recovery) {
        Vec3 direction=HeadingController.horizontal(point.subtract(maid.getEyePosition()));
        var result=new GlidePlanner.Request(Math.toDegrees(Math.atan2(-direction.x,direction.z)),height,distance,slope,
            attack?runFlare:SkyfarerSettings.get("level_run_distance"),Double.NaN,fireworks.remainingBoostTicks(),
            attack?SkyfarerSettings.attackTurnRate():SkyfarerSettings.turnRate(),
            (attack||recovery)&&runFlare<0?SkyfarerSettings.get("slope_pitch_rate"):SkyfarerSettings.pitchRate(),
            (attack||recovery)&&runFlare<0?SkyfarerSettings.get("slope_pull_up_pitch_rate"):recovery?SkyfarerSettings.get("recovery_pitch_rate"):SkyfarerSettings.pullUpPitchRate(),gravity(),
            SkyfarerSettings.get("corridor_side_margin"),SkyfarerSettings.get("corridor_vertical_margin"),recovery,aim);
        if(recovery && runFlare==-1 && laneExit!=null) result=result.withExit(new GlidePlanner.Exit(point.x,height,point.z,laneGradientX,laneGradientZ));
        return result;
    }
    private void applyPlan() {
        heading.steerAngles(maid,flightPlan.yaw(),flightPlan.pitch(),flightPlan.yawRate(),flightPlan.pitchRate());
    }
    private void beginReturn(String reason) {
        compactGoal=null; chainCandidate=null; chainTarget=null;
        returnReason=reason;
        endCharge(); runTarget = null; preferred = null; secondaryTarget = null;
        landingPoint = null;
        returnTicks = 0; transition(AirCombatState.RETURN_TO_OWNER, reason);
    }
    private boolean returning() { return state == AirCombatState.RETURN_TO_OWNER || state == AirCombatState.LANDING || state == AirCombatState.EMERGENCY || state == AirCombatState.ABORT; }
    private void returnToAnchor() {
        SpearCompat.stopCharge(maid); returnTicks++;
        if (maid.onGround()) { diagnostic = "LANDED"; release(); return; }
        if (state!=AirCombatState.LANDING && AttackPolicy.canResume(returnReason,maid.isFallFlying(),SkyfarerEquipment.elytra(maid)&&SkyfarerEquipment.spear(maid),
            SkyfarerEquipment.fireworks(maid)||fireworks.active(),workBlockReason().isEmpty()&&SkyfarerRuntime.isSkyfarer(maid),
            airborneTicks<SkyfarerSettings.ticks("max_airborne_ticks"))
            && scanCadence.ready(maid.level().getGameTime(),SkyfarerSettings.ticks("scan_interval_ticks"))) {
            combatAnchor=CombatAnchor.current(maid); scan();
            if (preferred!=null && maid.position().distanceTo(anchor())<=TargetEvaluator.hardRange(maid)+8) {
                pendingReturn=""; landingPoint=null; returnTicks=0; startApproach(); approach(); return;
            }
        }
        var owner = maid.getOwner();
        if (!maid.isHomeModeEnable() && (owner == null || owner.level() != maid.level())) {
            steer("SAFE_DESCENT", maid.getEyePosition().add(forward().scale(12)).add(0, -2, 0), false, false); return;
        }
        Vec3 destination = CombatAnchor.current(maid);
        if(state!=AirCombatState.LANDING && maid.isFallFlying() && destination.subtract(maid.position()).horizontalDistance()>10
            && CompactTurnPlanner.angle(predictionStart(),destination.x,destination.z)>40) {
            if(compactGoal==null) compactGoal=new CompactTurnPlanner.Goal(destination.x,destination.y+6,destination.z,maid.getX(),maid.getZ(),
                Math.max(32,destination.subtract(maid.position()).horizontalDistance()+16));
            flyCompactTurn();
            if(returnTicks<=SkyfarerSettings.ticks("return_timeout_ticks")) return;
        } else compactGoal=null;
        if (landingPoint == null || returnTicks % 20 == 0 || landingPoint.distanceToSqr(destination) > 144)
            landingPoint = AirCollisionChecker.landingNear(maid, destination);
        Vec3 landing = landingPoint == null ? destination.add(0, 6, 0) : landingPoint;
        Vec3 to = landing.subtract(maid.position());
        if (landingPoint != null && state != AirCombatState.LANDING && to.horizontalDistance() < 10)
            transition(AirCombatState.LANDING, maid.isHomeModeEnable() ? "LANDING_NEAR_HOME_CHARGE_OFF" : "LANDING_NEAR_OWNER_CHARGE_OFF");
        if (state != AirCombatState.LANDING || landingPoint == null) {
            steer(maid.isHomeModeEnable() ? "RETURN_ABOVE_HOME" : "RETURN_ABOVE_OWNER", landing.add(0, 5, 0), false, false); manageEnergy();
            if (maid.isFallFlying()) terrainSafety();
        } else steer("LANDING", landing, false, false);
        if (returnTicks > SkyfarerSettings.ticks("return_timeout_ticks")) {
            if (maid.isHomeModeEnable()) {
                // Only a loaded, collision-free standing location found by landingNear is usable.
                Vec3 safe = AirCollisionChecker.landingNear(maid, destination);
                if (safe != null) { diagnostic = "RETURN_TIMEOUT_HOME_TELEPORT"; maid.teleportTo(safe.x, safe.y, safe.z); release(); }
            } else if (owner != null) { diagnostic = "RETURN_TIMEOUT_SAFETY_TELEPORT"; maid.teleportToOwner(owner); }
        }
    }
    private double approachSpeed() {
        // An attached rocket can accelerate much faster than last tick's measured displacement.
        return Math.max(fireworks.active() ? 1.7 : 0.25,
            Math.max(movement.horizontalDistance(), maid.getDeltaMovement().horizontalDistance()));
    }
    private void retryAnotherTarget(String reason) {
        endCharge();
        if(preferred!=null) { passHistory.begin(preferred.getUUID()); passHistory.finish(); }
        runTarget=null; scan(); startApproach(); diagnostic=reason;
    }
    private void sampleTargetMotion() {
        LivingEntity target = runTarget != null ? runTarget : preferred;
        if (target == null) { motionTarget = null; lastTargetPosition = null; targetMotion = Vec3.ZERO; return; }
        // Grounded entities retain a downward gravity velocity even while their position is stationary.
        targetMotion = target.getUUID().equals(motionTarget) && lastTargetPosition != null
            ? target.position().subtract(lastTargetPosition) : Vec3.ZERO;
        if (targetMotion.lengthSqr() > 9) targetMotion = Vec3.ZERO;
        motionTarget = target.getUUID(); lastTargetPosition = target.position();
    }
    private void manageEnergy() {
        if (flightPlan==null || flightRequest==null || !maid.isFallFlying() || state==AirCombatState.LANDING) return;
        if(state==AirCombatState.REPOSITION || state==AirCombatState.CHAIN_ATTACK) return;
        double minimum=SkyfarerSettings.get("minimum_boost_speed"), reserve=SkyfarerSettings.get("energy_reserve");
        boolean precisionAttack=flightRequest.aim()!=null || !route.waypoints().isEmpty() && route.waypoints().getFirst().name().equals("MERGE_SLOPE_LINE");
        if(precisionAttack) {
            minimum=Math.max(SkyfarerSettings.get("attack_minimum_boost_speed"),SpearCompat.minimumDamageSpeed(maid.getMainHandItem())+.05);
            reserve=SkyfarerSettings.get("attack_energy_reserve");
        }
        var coast=GlidePlanner.forecast(predictionStart(),flightRequest,flightPlan,corridor,minimum);
        energyReserve=coast.reserve();
        if (GlidePlanner.ordinary(flightPlan) && coast.safe() && energyReserve>=reserve) return;
        // Never boost an outward exit merely to reach a nominal cruise reserve.
        double speed=maid.getDeltaMovement().horizontalDistance();
        if(state==AirCombatState.APPROACH && coast.safe() && speed>.45 && TargetEvaluator.attackable(maid,preferred)) {
            double poweredEntry=AttackPolicy.minimumEntry(1.7,SpearCompat.delayTicks(maid.getMainHandItem()),SpearCompat.mobReach(maid.getMainHandItem()));
            if(flatTerrain(sampleSlope(preferred))) poweredEntry=Math.max(poweredEntry,
                DiveProfile.minimumDistance(maid.getEyeY()-attackHeight(preferred),1.7,gravity()));
            // Do not invalidate a usable slow entry by suddenly budgeting it at rocket speed.
            if(maid.position().subtract(preferred.position()).horizontalDistance()<poweredEntry+6) return;
        }
        if(state==AirCombatState.ATTACK_EXIT && coast.safe() && (speed>.6 || maid.getDeltaMovement().y>=0)) return;
        if(runFlare==-2 && state.locksRunTarget() && coast.safe() && energyReserve>=0 && speed>.6) return;
        double yaw=Math.toRadians(flightRequest.yaw());
        Vec3 pulseHeading=flightRequest.flare()<0?new Vec3(-Math.sin(yaw),0,Math.cos(yaw)):heading.desired();
        if (fireworks.active()) return;
        final var original=flightRequest;
        final double requiredSpeed=minimum,requiredReserve=reserve;
        boolean aligned=HeadingController.horizontalAngle(forward(),pulseHeading)<=20;
        Vec3 destination=returning()?CombatAnchor.current(maid):TargetEvaluator.attackable(maid,preferred)?preferred.position():anchor();
        if(!precisionAttack && state!=AirCombatState.ATTACK_EXIT && CompactTurnPlanner.angle(predictionStart(),destination.x,destination.z)>30) return;
        // The whole pulse must stay in the bounded, entity-ticking navigation corridor.
        boolean boosted=aligned && fireworks.boost(maid,duration -> GlidePlanner.pulseCost(predictionStart(),original,navigationSpace(),duration,requiredSpeed,requiredReserve));
        if (boosted) {
            flightRequest=flightRequest.withPulse(fireworks.remainingBoostTicks());
            flightPlan=GlidePlanner.choose(predictionStart(),flightRequest,corridor); applyPlan();
            if (!route.waypoints().isEmpty()) {
                var points=new java.util.ArrayList<>(route.waypoints());
                points.set(0,new AirRoute.Waypoint(points.getFirst().name(),maid.getEyePosition().add(heading.desired().scale(12))));
                route=new AirRoute(points);
            }
            if (SkyfarerSettings.debugLogging()) WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} ENERGY_PULSE reserve={} actualTicks={} mode={}",maid.getUUID(),energyReserve,fireworks.remainingBoostTicks(),flightPlan.mode());
        } else if(!coast.safe() && speed<.65 && Double.isFinite(clearance) && clearance<6
            && maid.getDeltaMovement().y<0 && TargetEvaluator.attackable(maid,preferred) && !returning()) {
            // A strike/straight exit can reject every pulse because it would overshoot contact or
            // the battle boundary. Recover toward a fixed local goal instead of silently stalling.
            var goal=new CompactTurnPlanner.Goal(preferred.getX(),attackHeight(preferred)+6,preferred.getZ(),
                maid.getX(),maid.getZ(),Math.max(32,preferred.position().subtract(maid.position()).horizontalDistance()+16));
            if(fireworks.boost(maid,duration->CompactTurnPlanner.pulseCost(predictionStart(),goal,duration,gravity(),Math.max(5,SkyfarerSettings.turnRate()),navigationSpace()))) {
                endCharge(); runTarget=null; compactGoal=goal;
                transition(AirCombatState.REPOSITION,"LOW_ENERGY_RECOVERY_TURN");
                flyCompactTurn();
            }
        }
    }
    private void steer(String name, Vec3 point, boolean attack, boolean pullUp) {
        if (maid.isFallFlying() && state!=AirCombatState.TAKEOFF && state!=AirCombatState.LANDING) {
            guide(name,point,point.y,attack,0,0,Double.NaN); return;
        }
        waypoint = point; route = new AirRoute(List.of(new AirRoute.Waypoint(name, point))); heading.steer(maid, point, attack, pullUp);
    }
    private void transition(AirCombatState next, String reason) {
        diagnostic = reason;
        if (state == next) return;
        boolean idleScan = state == AirCombatState.GROUNDED && next == AirCombatState.TARGET_ACQUIRE || state == AirCombatState.TARGET_ACQUIRE && next == AirCombatState.GROUNDED;
        if (SkyfarerSettings.debugLogging() && !idleScan) WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} {} -> {}; reason={}; run={}", maid.getUUID(), state, next, reason, runTarget == null ? "none" : runTarget.getUUID());
        state = next; stateTicks = 0;
        if (next == AirCombatState.TAKEOFF_PREP) { managed = true; airborneTicks = 0; pendingReturn = ""; }
    }
    public void release() {
        compactGoal=null; chainCandidate=null; chainTarget=null; chainCenter=null; sampledSlope=null;
        if(state==AirCombatState.WATER_ESCAPE) maid.getSwimManager().setWantToSwim(previousWantToSwim);
        endCharge(); fireworks.detach(); ElytraFlightController.stopGliding(maid);
        if (managed) TlmFollowCompat.clearGroundMovement(maid);
        managed = false; route = AirRoute.EMPTY; runTarget = null; preferred = null; combatAnchor = null;
        pendingReturn = ""; movement = Vec3.ZERO; lastPosition = maid.position();
        motionTarget = null; lastTargetPosition = null; targetMotion = Vec3.ZERO;
        secondaryTarget = null; multiDirection = Vec3.ZERO; surfaceLaunch=null; launchControlTicks=0; waterReturnReason="";
        laneExit=null; recoveryOrigin=null; flightPlan=null; flightRequest=null;
        transition(AirCombatState.GROUNDED, diagnostic); retryAt = maid.level().getGameTime() + SkyfarerSettings.ticks("launch_retry_ticks");
    }
    private void endCharge() {
        chargeTailUntil = -1;
        SpearCompat.stopCharge(maid);
        passHistory.finish();
    }
    private void logDiagnostic() {
        if (!SkyfarerSettings.debugLogging() || !logCadence.ready(maid.level().getGameTime(), SkyfarerSettings.ticks("log_interval_ticks"))) return;
        WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} state={} reason={} scan=[{}] pos={} velocity={} ground={} glide={} clearance={} preferred={} run={} using={} chargeTicks={} spearReach={}",
            maid.getUUID(), state, diagnostic, lastScan, maid.position(), movement, maid.onGround(), maid.isFallFlying(), clearance,
            preferred == null ? "none" : preferred.getUUID(), runTarget == null ? "none" : runTarget.getUUID(), maid.isUsingItem(), maid.getTicksUsingItem(), SpearCompat.mobReach(maid.getMainHandItem()));
        if (flightPlan!=null) WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} planner={} safe={} clearTicks={} energyReserve={} rocketTicks={} exit={} corridor=[{}] pitch={}",
            maid.getUUID(),flightPlan.mode(),flightPlan.safe(),flightPlan.clearTicks(),energyReserve,fireworks.remainingBoostTicks(),exitStyle,laneStatus,flightPlan.pitch());
        if(state==AirCombatState.REPOSITION && preferred!=null && compactGoal!=null) {
            var slope=sampleSlope(preferred);
            WineFoxSkyfarer.LOGGER.info("[WFS debug] maid={} entryDistance={} entryAngle={} staging={} goalY={} supportY={} targetFeetY={} slopeNear=({}, {}) slopeWide=({}, {}) samples={} radius={} aimOffset={}",
                maid.getUUID(),maid.position().subtract(preferred.position()).horizontalDistance(),CompactTurnPlanner.angle(predictionStart(),preferred.getX(),preferred.getZ()),
                compactGoal.staging(),compactGoal.y(),corridor.supportSample(preferred.getX(),preferred.getZ(),preferred.getY()),preferred.getY(),
                slope.nearX(),slope.nearZ(),slope.wideX(),slope.wideZ(),slope.samples(),SkyfarerSettings.get("slope_sample_radius"),SkyfarerSettings.get("attack_aim_offset"));
        }
    }
    public CompoundTag debugSnapshot() {
        var activeTarget=state==AirCombatState.CHAIN_ATTACK?chainTarget:runTarget;
        var data = DebugSnapshot.make(maid, state, preferred, activeTarget, previous, score, heading.desired(), heading.turnAngle(), route);
        if(debugEntry!=null&&debugEntryDirection!=null&&(state==AirCombatState.APPROACH||state==AirCombatState.ATTACK_RUN||state==AirCombatState.DIVE||state==AirCombatState.SPEAR_CHARGE)){
            DebugSnapshot.vector(data,"attack_entry",debugEntry);DebugSnapshot.vector(data,"entry_direction",debugEntryDirection);
        }
        if(maid.isFallFlying() && corridor!=null) {
            if(surfaceLaunch!=null&&(launchControlTicks>0||state==AirCombatState.WATER_ESCAPE))DebugSnapshot.corridor(data,maid,SurfaceLaunchPlanner.trace(predictionStart(),surfaceLaunch,SurfaceLaunchPlanner.DEPARTURE_TICKS-launchControlTicks,gravity(),fireworks.remainingBoostTicks(),corridor,state==AirCombatState.WATER_ESCAPE?this::predictedWater:SurfaceLaunchPlanner.DRY));
            else if(compactGoal!=null&&compactPlan!=null)DebugSnapshot.corridor(data,maid,CompactTurnPlanner.trace(predictionStart(),compactGoal,compactPlan,fireworks.remainingBoostTicks(),gravity(),Math.max(5,SkyfarerSettings.turnRate()),corridor));
            else if(flightPlan!=null&&flightRequest!=null)DebugSnapshot.corridor(data,maid,GlidePlanner.trace(predictionStart(),flightRequest,flightPlan,corridor));
        }
        data.putString("maid_name", maid.getName().getString()); data.putString("reason", diagnostic); data.putString("scan", lastScan);
        data.putDouble("clearance", clearance); data.putBoolean("charging", maid.isUsingItem() && SpearCompat.isChargeWeapon(maid.getUseItem()));
        data.putInt("charge_ticks", maid.getTicksUsingItem()); data.putInt("charge_delay", SpearCompat.delayTicks(maid.getMainHandItem()));
        data.putDouble("pitch", maid.getXRot());
        if (activeTarget != null) {
            data.putDouble("target_distance", maid.distanceTo(activeTarget));
            data.putDouble("above_target", maid.getEyeY() - activeTarget.getBoundingBox().getCenter().y);
        }
        if(flightPlan!=null) data.putDouble("turn_rate",flightPlan.yawRate());
        data.putDouble("spear_reach", SpearCompat.mobReach(maid.getMainHandItem())); DebugSnapshot.vector(data, "measured_velocity", movement); return data;
    }
}
