package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.AsyncPlanning;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.mixin.LivingRiptideAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.AABB;

/** Server-only WineFox extension: independent precharge and OFF_HAND item release, then a certified metered burst.
 * Only this maid's owned thrust/spin is controlled. Player tridents and Spears are untouched. */
public final class RiptidePropulsion {
    private RiptidePlanner.Plan plan;
    private ItemStack used=ItemStack.EMPTY;
    private long started;
    private long releasedAt=-1;
    private long lastThrustAt=-1;
    private boolean released;
    private String context="NONE";
    // Only the synchronous, certified release call may activate the non-player adapter.
    private record Release(EntityMaid maid,RiptidePropulsion owner) {}
    private static final ThreadLocal<Release> RELEASING=new ThreadLocal<>();
    private boolean launchPendingJump,jumpRequested;
    public boolean active(){return plan!=null;}
    public int selectedDuration(){return plan==null?0:plan.duration();}
    public boolean completed(){return released&&"COMPLETED".equals(endReason);}
    private String endReason="";
    private String beginRejection="NONE";
    public String beginRejection(){return beginRejection;}
    public void context(String state){context=state;}
    private boolean ownsUse(EntityMaid m){return m.isUsingItem()&&m.getUsedItemHand()==InteractionHand.OFF_HAND&&m.getUseItem()==used&&m.getOffhandItem()==used;}
    private boolean reserveReady(EntityMaid m,String event) {
        released=false;releasedAt=-1;lastThrustAt=-1;endReason="";started=m.level().getGameTime();
        log(m,"READY_RESERVED:"+event);return true;
    }
    public void prepare(EntityMaid m,boolean eligible){RiptideReadiness.of(m).tick(m,eligible,active()&&released);}
    public int preparedTicks(EntityMaid m){return RiptideReadiness.of(m).ticks();}
    static boolean contains(EntityMaid m,ItemStack item){
        if(item==m.getOffhandItem())return true;
        var inv=m.getAvailableBackpackInv();
        for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i)==item&&!inv.extractItem(i,1,true).isEmpty())return true;
        return false;
    }
    static ItemStack prechargeItem(EntityMaid m){
        if(!m.isInWaterOrRain())return ItemStack.EMPTY;
        var inv=m.getAvailableBackpackInv();ItemStack best=strength(m,m.getOffhandItem())>0?m.getOffhandItem():ItemStack.EMPTY;
        for(int i=0;i<inv.getSlots();i++){
            var stack=inv.getStackInSlot(i);var old=m.getOffhandItem();
            if(strength(m,stack)<=strength(m,best)||stack.getCount()!=1||inv.extractItem(i,1,true).isEmpty()
                ||!old.isEmpty()&&(!inv.isItemValid(i,old)||old.getCount()>inv.getSlotLimit(i)))continue;
            best=stack;
        }
        return best;
    }
    static float strength(EntityMaid m,ItemStack s) {
        return s.is(Items.TRIDENT)&&(!s.isDamageableItem()||s.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)||s.getDamageValue()<s.getMaxDamage()-1)?EnchantmentHelper.getTridentSpinAttackStrength(s,m):0;
    }
    public static float available(EntityMaid m) {
        return strengths(m).stream().max(Float::compare).orElse(0f);
    }
    public static java.util.List<Float> strengths(EntityMaid m) {
        float value=strength(m,prechargeItem(m));
        // Strength changes the duration cap, not thrust per tick: the highest usable cap
        // contains every shorter candidate, so lower caps add no distinct trajectory.
        return value>0&&Float.isFinite(value)?java.util.List.of(value):java.util.List.of();
    }
    public boolean ready(EntityMaid m){return !active()&&!m.isAutoSpinAttack()&&RiptideReadiness.of(m).ready(m,RiptideReadiness.of(m).strength());}
    private boolean equip(EntityMaid m,float wanted) {
        if(strength(m,m.getOffhandItem())==wanted){used=m.getOffhandItem();return true;}
        var inv=m.getAvailableBackpackInv();var old=m.getOffhandItem();
        for(int i=0;i<inv.getSlots();i++) {
            var stack=inv.getStackInSlot(i);
            if(strength(m,stack)!=wanted||stack.getCount()!=1||inv.extractItem(i,1,true).isEmpty()
                ||!old.isEmpty()&&(!inv.isItemValid(i,old)||old.getCount()>inv.getSlotLimit(i)))continue;
            inv.setStackInSlot(i,old);m.setItemSlot(EquipmentSlot.OFFHAND,stack);used=stack;return true;
        }
        return false;
    }
    /** Native spin reverses on any living contact. Exclude those paths, including allies. */
    private boolean entitiesClear(EntityMaid m,RiptidePlanner.Plan p,int elapsed) {
        var anchor=context.equals("TRANSIT")?m.position():dev.winefox.skyfarer.ai.CombatAnchor.current(m);
        double radius=context.equals("TRANSIT")?128:Math.max(dev.winefox.skyfarer.ai.TargetEvaluator.hardRange(m)+12,m.position().subtract(anchor).horizontalDistance()+6);
        for(int i=1;i<p.trace().size();i++) {
            var point=p.trace().get(i);if(Math.hypot(point.x()-anchor.x,point.z()-anchor.z)>radius)return false;
            int tick=elapsed+i-1;if(tick<RiptidePlanner.WINDUP||tick>=p.end())continue;
            var a=p.trace().get(i-1);var b=p.trace().get(i);double r=m.getBbWidth()*.5+.3;
            var box=new AABB(Math.min(a.x(),b.x())-r,Math.min(a.y(),b.y())-m.getEyeHeight()-.2,Math.min(a.z(),b.z())-r,
                Math.max(a.x(),b.x())+r,Math.max(a.y(),b.y())+m.getBbHeight()-m.getEyeHeight()+.2,Math.max(a.z(),b.z())+r);
            if(!m.level().getEntitiesOfClass(LivingEntity.class,box,e->e!=m&&e.isAlive()).isEmpty())return false;
        }
        return true;
    }
    public boolean begin(EntityMaid m,RiptidePlanner.Plan candidate,FlightPrediction.State s,AsyncPlanning planning,HeadingController heading) {
        beginRejection="AIR_USE_OR_ENVIRONMENT_UNAVAILABLE";
        if(candidate==null||!ready(m)||m.onGround()||!m.isFallFlying())return false;
        var proof=planning.certifyRiptide(s,candidate,RiptidePlanner.WINDUP,m.isInWaterOrBubble());
        if(proof==null){beginRejection=planning.riptideRejection();return false;}
        if(!entitiesClear(m,proof,RiptidePlanner.WINDUP)){beginRejection="ENTITY_OR_ANCHOR_REJECTED";return false;}
        if(!equip(m,proof.strength())){beginRejection="OFFHAND_EQUIP_REJECTED";return false;}
        plan=proof;
        beginRejection="READY_RESERVATION_REJECTED";
        reserveReady(m,"AIR_READY");return tick(m,s,planning,heading);
    }
    /** Reserve READY on the ground, then a normal JumpControl jump; release only AFTER leaving the floor.
     * This uses vanilla's airborne release, not a fabricated ground lift or a velocity teleport. */
    public static FlightPrediction.State jumpStart(EntityMaid m,PhysicsContext physics) {
        var v=m.getDeltaMovement();
        return new FlightPrediction.State(m.getX()+v.x,m.getY()+.82,m.getZ()+v.z,v.x*.91,(.42-physics.gravity())*.98,v.z*.91,m.getYRot(),m.getXRot(),physics.nextTick());
    }
    public boolean beginLaunch(EntityMaid m,RiptidePlanner.Plan candidate,AsyncPlanning planning,HeadingController heading) {
        beginRejection="LAUNCH_USE_OR_ENVIRONMENT_UNAVAILABLE";
        if(candidate==null||!ready(m)||!m.onGround()||m.isInWaterOrBubble())return false;
        if(!jumpClear(m)){beginRejection="JUMP_BODY_BLOCKED";return false;}
        var proof=planning.certifyRiptide(RiptidePlanner.aimLaunch(jumpStart(m,planning.physics()),candidate.yaw(),candidate.pitch()),candidate,RiptidePlanner.WINDUP,false);
        if(proof==null){beginRejection=planning.riptideRejection();return false;}
        if(!entitiesClear(m,proof,RiptidePlanner.WINDUP)){beginRejection="ENTITY_OR_ANCHOR_REJECTED";return false;}
        if(!equip(m,proof.strength())){beginRejection="OFFHAND_EQUIP_REJECTED";return false;}
        plan=proof;launchPendingJump=true;jumpRequested=false;
        beginRejection="READY_RESERVATION_REJECTED";
        heading.steerAngles(m,plan.yaw(),plan.pitch(),180,90);reserveReady(m,"LAUNCH_READY");return tick(m,jumpStart(m,planning.physics()),planning,heading);
    }
    private static boolean jumpClear(EntityMaid m) {
        return new dev.winefox.skyfarer.safety.CorridorProbe(m).segment(m.getEyePosition().add(0,.01,0),m.getEyePosition().add(0,.43,0),.02,0);
    }
    public SweepingAttackPlanner.Selection attackSelection(){
        if(!continuation()||plan.demand().exiting()||plan.demand().aim()==null)return null;
        var trace=plan.trace().stream().map(s->new GlidePlanner.TracePoint(s,.2,.15)).toList();
        var d=plan.demand();var first=RiptidePlanner.command(plan.trace().getFirst(),plan,RiptidePlanner.WINDUP,.08);
        // Contact time is telemetry; the complete continuation certificate authorized this plan.
        int contact=plan.duration();double miss=Double.POSITIVE_INFINITY;
        for(int i=plan.duration();i<plan.trace().size();i++){
            double value=d.aim().missSquared(plan.trace().get(i),i);if(value<miss){miss=value;contact=i;}
        }
        return new SweepingAttackPlanner.Selection(d.path(),new SweepingAttackPlanner.Rollout(true,true,contact,plan.cost(),miss,first,trace));
    }
    public boolean continuation(){return plan!=null&&plan.demand()!=null&&plan.demand().continuation();}
    public void continuationAim(FlightPrediction.Aim aim,int deadline){if(continuation()&&(plan.demand().exiting()||aim!=null))plan=plan.withDemand(plan.demand().withAim(aim).withDeadline(deadline));}
    public boolean tick(EntityMaid m,FlightPrediction.State s,AsyncPlanning planning,HeadingController heading) {
        if(plan==null)return false;
        int charge=RiptideReadiness.of(m).ticks();
        int elapsed=released?RiptidePlanner.WINDUP+(int)(m.level().getGameTime()-releasedAt):Math.min(charge,RiptidePlanner.WINDUP);
        if(!released) {
            if(!RiptideReadiness.of(m).ready(m,plan.strength())){cancel(m,"READINESS_ITEM_OR_EQUIPMENT_CHANGED");return false;}
            if(!m.isInWaterOrRain()){cancel(m,"ENVIRONMENT_LOST");return false;}
            if(strength(m,used)!=plan.strength()){cancel(m,"TRIDENT_CHANGED_OR_UNUSABLE");return false;}
            if(m.level().getGameTime()-started>40){cancel(m,"CHARGE_OR_JUMP_TIMEOUT");return false;}
            if((m.level().getGameTime()-started)%5==0)log(m,"READY_CHECK");
        }
        if(launchPendingJump) {
            if(m.onGround()) {
                if(jumpRequested||!jumpClear(m)){cancel(m,"LAUNCH_JUMP_BLOCKED");return false;}
                var proof=planning.certifyRiptide(jumpStart(m,planning.physics()),plan,RiptidePlanner.WINDUP,false);
                if(proof==null){cancel(m,"LAUNCH_"+planning.riptideRejection());return false;}
                if(!entitiesClear(m,proof,RiptidePlanner.WINDUP)){cancel(m,"LAUNCH_ENTITY_OR_ANCHOR_REJECTED");return false;}
                heading.steerAngles(m,plan.yaw(),plan.pitch(),180,90);
                if(elapsed>=RiptidePlanner.WINDUP){m.getJumpControl().jump();jumpRequested=true;log(m,"LAUNCH_JUMP");}
                return true;
            }
            if(!jumpRequested||!ElytraFlightController.startGliding(m)){cancel(m,"LAUNCH_GLIDE_REJECTED");return false;}
            s=new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.getDeltaMovement().x,m.getDeltaMovement().y,m.getDeltaMovement().z,m.getYRot(),m.getXRot(),planning.physics());
            launchPendingJump=false;elapsed=RiptidePlanner.WINDUP;
        }
        // Only WineFox's owned spin/thrust is shortened. Momentum and FALL_FLYING survive cutoff.
        if(released&&elapsed>=(plan.guidedRecovery()?plan.handoff():plan.end())){
            boolean ready=plan.departure()?RiptidePlanner.departureReady(s,plan):!plan.guidedRecovery()||Math.hypot(s.vx(),s.vz())>=plan.minimumSpeed()&&s.y()>=plan.height()-1;
            cancel(m,ready?"COMPLETED":"HANDOFF_NOT_READY");return false;
        }
        boolean recovering=released&&elapsed>=plan.end();
        if(recovering){
            if(elapsed==plan.end()){clearSpin(m);log(m,"RECOVERY_BEGIN");}
        } else if(released&&(!m.isInWaterOrRain()||strength(m,used)!=plan.strength()||!m.isAutoSpinAttack())){cancel(m,"THRUST_ENVIRONMENT_ITEM_OR_SPIN_LOST");return false;}
        if(m.onGround()||!m.isFallFlying()||m.getOffhandItem()!=used
            ){cancel(m,"GROUND_GLIDE_OR_STACK_CHANGED");return false;}
        var proof=planning.certifyRiptide(s,plan,elapsed,m.isInWaterOrBubble());
        if(proof==null){cancel(m,planning.riptideRejection());return false;}
        if(!entitiesClear(m,proof,elapsed)){cancel(m,"ENTITY_OR_ANCHOR_REJECTED");return false;}
        var command=RiptidePlanner.command(s,plan,elapsed,planning.physics().gravity());
        heading.steerAngles(m,command.yaw(),command.pitch(),command.yawRate(),command.pitchRate());
        if(!released&&elapsed==RiptidePlanner.WINDUP) {
            log(m,"RELEASE_REQUEST:CERTIFIED_READY");
            RELEASING.set(new Release(m,this));
            try {
                int remaining=used.getUseDuration(m)-RiptidePlanner.WINDUP;
                // WineFox's independent 10-tick resource authorizes this local item release.
                // Do not call LivingEntity.releaseUsingItem: the main hand may be charging Spears.
                if(!net.neoforged.neoforge.event.EventHooks.onUseItemStop(m,used,remaining))
                    used.releaseUsing(m.level(),m,remaining);
            } finally {RELEASING.remove();}
            if(!released){cancel(m,"RELEASE_VETOED_OR_CALLBACK_MISSING");return false;}
            log(m,"RELEASED");
        } else if(released&&!recovering&&lastThrustAt!=m.level().getGameTime()) {
            thrust(m);if((elapsed-RiptidePlanner.WINDUP)%5==0)log(m,"THRUST");
        }
        return true;
    }
    /** Invoked from TridentItem.releaseUsing, after NeoForge's onUseItemStop gate. */
    public static void releaseAuthorized(EntityMaid m,ItemStack stack,int remaining) {
        var call=RELEASING.get();if(call==null||call.maid()!=m)return;
        var self=call.owner();
        if(self.released||self.plan==null||m.getOffhandItem()!=stack||stack!=self.used||!m.isInWaterOrRain()
            ||stack.getUseDuration(m)-remaining<RiptidePlanner.WINDUP||strength(m,stack)!=self.plan.strength())return;
        if(!RiptideReadiness.of(m).consume(m,self.plan.strength()))return;
        var before=m.getDeltaMovement();
        stack.hurtAndBreak(1,m,EquipmentSlot.OFFHAND);self.thrust(m);
        var spin=(LivingRiptideAccessor)m;spin.wfs$spinTicks(self.plan.duration());spin.wfs$spinDamage(8);spin.wfs$spinItem(stack);spin.wfs$livingFlag(4,true);
        self.released=true;self.releasedAt=m.level().getGameTime();
        var sound=EnchantmentHelper.pickHighestLevel(stack,EnchantmentEffectComponents.TRIDENT_SOUND).orElse(net.minecraft.sounds.SoundEvents.TRIDENT_THROW);
        m.level().playSound(null,m,sound.value(),m.getSoundSource(),1,1);
        self.log(m,"READY_RELEASE_CALLBACK impulse="+m.getDeltaMovement().subtract(before)+" spinStarted="+m.isAutoSpinAttack());
    }
    private void thrust(EntityMaid m) {
        var impulse=RiptidePlanner.impulse(m.getYRot(),m.getXRot(),RiptidePlanner.THRUST);
        m.push(impulse.x(),impulse.y(),impulse.z());lastThrustAt=m.level().getGameTime();
    }
    public void observedStop(EntityMaid m,String caller){if(active())log(m,"USE_STOP independentReady=true caller="+caller+" releaseScope="+(RELEASING.get()!=null));}
    public void cancel(EntityMaid m){cancel(m,"CALLER_CANCEL");}
    public void cancel(EntityMaid m,String reason) {
        if(plan==null)return;
        endReason=reason;if(!released)beginRejection=reason;log(m,"END:"+reason);
        // Independent readiness never stops another item-use session.
        if(released)clearSpin(m);
        // Stop owned WineFox propulsion only. Velocity and FALL_FLYING are never reset here.
        plan=null;used=ItemStack.EMPTY;launchPendingJump=false;jumpRequested=false;
    }
    private static void clearSpin(EntityMaid m){var spin=(LivingRiptideAccessor)m;spin.wfs$spinTicks(0);spin.wfs$spinDamage(0);spin.wfs$spinItem(ItemStack.EMPTY);spin.wfs$livingFlag(4,false);}
    public void transition(EntityMaid m,String from,String to,String reason){if(active())log(m,"STATE_TRANSITION "+from+"->"+to+":"+reason);context=to;}
    private static int level(ItemStack stack){return stack.getEnchantments().entrySet().stream().filter(e->e.getKey().is(Enchantments.RIPTIDE)).mapToInt(e->e.getIntValue()).max().orElse(0);}
    private void log(EntityMaid m,String event){if(dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging())
        dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] RIPTIDE maid={} event={} state={} selectedHand=OFF_HAND item={} usedHand={} startTick={} tick={} prepared={} charge={} remaining={} sameUse={} using={} wet={} enchantLevel={} strength={} duration={} maxDuration={} departure={} thrustElapsed={} released={} spin={} velocity={}",
            m.getUUID(),event,context,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(used.getItem()),m.getUsedItemHand(),started,m.level().getGameTime(),preparedTicks(m),m.getTicksUsingItem(),m.getUseItemRemainingTicks(),ownsUse(m),m.isUsingItem(),m.isInWaterOrRain(),level(used),plan==null?0:plan.strength(),selectedDuration(),plan==null?0:RiptidePlanner.maximumTicks(plan.strength()),plan!=null&&plan.departure(),released?m.level().getGameTime()-releasedAt:0,released,m.isAutoSpinAttack(),m.getDeltaMovement());}
}
