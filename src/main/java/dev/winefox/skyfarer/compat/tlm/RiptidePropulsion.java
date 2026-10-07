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

/** Server-only item lifecycle. TridentItem itself only releases for Player, so adapt its
 * native additive impulse and LivingEntity spin lifecycle to the maid. No projectile/extra damage. */
public final class RiptidePropulsion {
    private RiptidePlanner.Plan plan;
    private ItemStack used=ItemStack.EMPTY;
    private long started;
    private long releasedAt=-1;
    private boolean released;
    private String context="NONE";
    // Only the synchronous, certified release call may activate the non-player adapter.
    private record Release(EntityMaid maid,RiptidePropulsion owner) {}
    private static final ThreadLocal<Release> RELEASING=new ThreadLocal<>();
    private boolean launchCharging,jumpRequested;
    public boolean active(){return plan!=null;}
    public boolean completed(){return released&&"COMPLETED".equals(endReason);}
    private String endReason="";
    public void context(String state){context=state;}
    private boolean ownsUse(EntityMaid m){return m.isUsingItem()&&m.getUsedItemHand()==InteractionHand.OFF_HAND&&m.getUseItem()==used&&m.getOffhandItem()==used;}
    private boolean startUse(EntityMaid m,String event) {
        released=false;releasedAt=-1;endReason="";started=m.level().getGameTime();
        m.startUsingItem(InteractionHand.OFF_HAND);
        if(!ownsUse(m)){cancel(m,"START_USE_REJECTED");return false;}
        log(m,event);return true;
    }
    private static float strength(EntityMaid m,ItemStack s) {
        return s.is(Items.TRIDENT)&&s.getDamageValue()<s.getMaxDamage()-1?EnchantmentHelper.getTridentSpinAttackStrength(s,m):0;
    }
    public static float available(EntityMaid m) {
        return strengths(m).stream().max(Float::compare).orElse(0f);
    }
    public static java.util.List<Float> strengths(EntityMaid m) {
        if(!m.isInWaterOrRain())return java.util.List.of();
        var values=new java.util.TreeSet<Float>();values.add(strength(m,m.getOffhandItem()));var inv=m.getAvailableBackpackInv();
        for(int i=0;i<inv.getSlots();i++)if(!inv.extractItem(i,1,true).isEmpty())values.add(strength(m,inv.getStackInSlot(i)));
        return values.stream().filter(f->f>0&&Float.isFinite(f)).limit(3).toList();
    }
    public boolean ready(EntityMaid m){return !active()&&!m.isAutoSpinAttack()&&available(m)>0;}
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
    private static boolean entitiesClear(EntityMaid m,RiptidePlanner.Plan p,int elapsed) {
        var anchor=dev.winefox.skyfarer.ai.CombatAnchor.current(m);
        double radius=Math.max(dev.winefox.skyfarer.ai.TargetEvaluator.hardRange(m)+12,m.position().subtract(anchor).horizontalDistance()+6);
        for(int i=1;i<p.trace().size();i++) {
            var point=p.trace().get(i);if(Math.hypot(point.x()-anchor.x,point.z()-anchor.z)>radius)return false;
            int tick=elapsed+i-1;if(tick<RiptidePlanner.WINDUP||tick>=RiptidePlanner.WINDUP+RiptidePlanner.SPIN)continue;
            var a=p.trace().get(i-1);var b=p.trace().get(i);double r=m.getBbWidth()*.5+.3;
            var box=new AABB(Math.min(a.x(),b.x())-r,Math.min(a.y(),b.y())-m.getEyeHeight()-.2,Math.min(a.z(),b.z())-r,
                Math.max(a.x(),b.x())+r,Math.max(a.y(),b.y())+m.getBbHeight()-m.getEyeHeight()+.2,Math.max(a.z(),b.z())+r);
            if(!m.level().getEntitiesOfClass(LivingEntity.class,box,e->e!=m&&e.isAlive()).isEmpty())return false;
        }
        return true;
    }
    public boolean begin(EntityMaid m,RiptidePlanner.Plan candidate,FlightPrediction.State s,AsyncPlanning planning,HeadingController heading) {
        if(candidate==null||!ready(m)||m.onGround()||!m.isFallFlying()||m.isUsingItem())return false;
        var proof=planning.certifyRiptide(s,candidate,0,m.isInWaterOrBubble());
        if(proof==null||!entitiesClear(m,proof,0)||!equip(m,proof.strength()))return false;
        plan=proof;
        heading.steerAngles(m,plan.yaw(),plan.pitch(),8,8);return startUse(m,"WINDUP");
    }
    /** Charge on the ground, then a normal JumpControl jump; release only AFTER leaving the floor.
     * This uses vanilla's airborne release, not a fabricated ground lift or a velocity teleport. */
    public static FlightPrediction.State jumpStart(EntityMaid m,PhysicsContext physics) {
        var v=m.getDeltaMovement();
        return new FlightPrediction.State(m.getX()+v.x,m.getY()+.82,m.getZ()+v.z,v.x*.91,(.42-physics.gravity())*.98,v.z*.91,m.getYRot(),m.getXRot(),physics.nextTick());
    }
    public boolean beginLaunch(EntityMaid m,RiptidePlanner.Plan candidate,AsyncPlanning planning,HeadingController heading) {
        if(candidate==null||!ready(m)||!m.onGround()||m.isUsingItem()||m.isInWaterOrBubble())return false;
        if(!jumpClear(m))return false;
        var proof=planning.certifyRiptide(RiptidePlanner.aimLaunch(jumpStart(m,planning.physics()),candidate.yaw(),candidate.pitch()),candidate,RiptidePlanner.WINDUP,false);
        if(proof==null||!entitiesClear(m,proof,RiptidePlanner.WINDUP)||!equip(m,proof.strength()))return false;
        plan=proof;launchCharging=true;jumpRequested=false;
        heading.steerAngles(m,plan.yaw(),plan.pitch(),180,90);return startUse(m,"LAUNCH_WINDUP");
    }
    private static boolean jumpClear(EntityMaid m) {
        return new dev.winefox.skyfarer.safety.CorridorProbe(m).segment(m.getEyePosition().add(0,.01,0),m.getEyePosition().add(0,.43,0),.02,0);
    }
    public boolean tick(EntityMaid m,FlightPrediction.State s,AsyncPlanning planning,HeadingController heading) {
        if(plan==null)return false;
        int charge=m.getTicksUsingItem();
        int elapsed=released?RiptidePlanner.WINDUP+(int)(m.level().getGameTime()-releasedAt):Math.min(charge,RiptidePlanner.WINDUP);
        if(!released) {
            if(!ownsUse(m)){cancel(m,"USE_SESSION_LOST_OR_HAND_CHANGED");return false;}
            if(!m.isInWaterOrRain()){cancel(m,"ENVIRONMENT_LOST");return false;}
            if(strength(m,used)!=plan.strength()){cancel(m,"TRIDENT_CHANGED_OR_UNUSABLE");return false;}
            if(m.level().getGameTime()-started>40){cancel(m,"CHARGE_OR_JUMP_TIMEOUT");return false;}
            if((m.level().getGameTime()-started)%5==0)log(m,"CHARGING");
        }
        if(launchCharging) {
            if(m.onGround()) {
                if(jumpRequested||!jumpClear(m)){cancel(m,"LAUNCH_JUMP_BLOCKED");return false;}
                var proof=planning.certifyRiptide(jumpStart(m,planning.physics()),plan,RiptidePlanner.WINDUP,false);
                if(proof==null||!entitiesClear(m,proof,RiptidePlanner.WINDUP)){cancel(m,"LAUNCH_PATH_REJECTED");return false;}
                heading.steerAngles(m,plan.yaw(),plan.pitch(),180,90);
                if(elapsed>=RiptidePlanner.WINDUP){m.getJumpControl().jump();jumpRequested=true;log(m,"LAUNCH_JUMP");}
                return true;
            }
            if(!jumpRequested||!ElytraFlightController.startGliding(m)){cancel(m,"LAUNCH_GLIDE_REJECTED");return false;}
            s=new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.getDeltaMovement().x,m.getDeltaMovement().y,m.getDeltaMovement().z,m.getYRot(),m.getXRot(),planning.physics());
            launchCharging=false;elapsed=RiptidePlanner.WINDUP;
        }
        // Recovery is a prediction horizon, not an extra use cooldown. Native spin owns its 20 ticks.
        if(released&&elapsed>=RiptidePlanner.WINDUP+RiptidePlanner.SPIN&&!m.isAutoSpinAttack()){cancel(m,"COMPLETED");return false;}
        if(m.onGround()||!m.isFallFlying()||m.getOffhandItem()!=used
            ){cancel(m,"GROUND_GLIDE_OR_STACK_CHANGED");return false;}
        var proof=planning.certifyRiptide(s,plan,elapsed,m.isInWaterOrBubble());
        if(proof==null){cancel(m,"PATH_OR_SNAPSHOT_REJECTED");return false;}
        if(!entitiesClear(m,proof,elapsed)){cancel(m,"ENTITY_OR_ANCHOR_REJECTED");return false;}
        double pitch=RiptidePlanner.pitch(s,plan,elapsed,planning.physics().gravity());
        heading.steerAngles(m,plan.yaw(),pitch,8,8);
        if(!released&&elapsed==RiptidePlanner.WINDUP) {
            log(m,"RELEASE_REQUEST:CERTIFIED_CHARGE_COMPLETE");
            RELEASING.set(new Release(m,this));
            try {m.releaseUsingItem();} finally {RELEASING.remove();}
            if(!released){cancel(m,"RELEASE_VETOED_OR_CALLBACK_MISSING");return false;}
            log(m,"RELEASED");
        }
        return true;
    }
    /** Invoked from TridentItem.releaseUsing, after NeoForge's onUseItemStop gate. */
    public static void releaseAuthorized(EntityMaid m,ItemStack stack,int remaining) {
        var call=RELEASING.get();if(call==null||call.maid()!=m)return;
        var self=call.owner();
        if(self.released||self.plan==null||!self.ownsUse(m)||stack!=self.used||!m.isInWaterOrRain()
            ||stack.getUseDuration(m)-remaining<RiptidePlanner.WINDUP||strength(m,stack)!=self.plan.strength())return;
        var impulse=RiptidePlanner.impulse(m.getYRot(),m.getXRot(),self.plan.strength());
        var before=m.getDeltaMovement();
        stack.hurtAndBreak(1,m,EquipmentSlot.OFFHAND);m.push(impulse.x(),impulse.y(),impulse.z());
        var spin=(LivingRiptideAccessor)m;spin.wfs$spinTicks(RiptidePlanner.SPIN);spin.wfs$spinDamage(8);spin.wfs$spinItem(stack);spin.wfs$livingFlag(4,true);
        self.released=true;self.releasedAt=m.level().getGameTime();
        var sound=EnchantmentHelper.pickHighestLevel(stack,EnchantmentEffectComponents.TRIDENT_SOUND).orElse(net.minecraft.sounds.SoundEvents.TRIDENT_THROW);
        m.level().playSound(null,m,sound.value(),m.getSoundSource(),1,1);
        self.log(m,"RELEASE_CALLBACK impulse="+m.getDeltaMovement().subtract(before)+" spinStarted="+m.isAutoSpinAttack());
    }
    public void observedStop(EntityMaid m,String caller){if(active()&&ownsUse(m))log(m,"USE_STOP caller="+caller+" releaseScope="+(RELEASING.get()!=null));}
    public void cancel(EntityMaid m){cancel(m,"CALLER_CANCEL");}
    public void cancel(EntityMaid m,String reason) {
        if(plan==null)return;
        endReason=reason;log(m,"END:"+reason);
        if(ownsUse(m))m.stopUsingItem();
        // Native spin is allowed to expire; velocity and FALL_FLYING are never reset here.
        plan=null;used=ItemStack.EMPTY;launchCharging=false;jumpRequested=false;
    }
    public void transition(EntityMaid m,String from,String to,String reason){if(active())log(m,"STATE_TRANSITION "+from+"->"+to+":"+reason);context=to;}
    private static int level(ItemStack stack){return stack.getEnchantments().entrySet().stream().filter(e->e.getKey().is(Enchantments.RIPTIDE)).mapToInt(e->e.getIntValue()).max().orElse(0);}
    private void log(EntityMaid m,String event){if(dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging())
        dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] RIPTIDE maid={} event={} state={} selectedHand=OFF_HAND item={} usedHand={} startTick={} tick={} charge={} remaining={} sameUse={} using={} wet={} enchantLevel={} strength={} released={} spin={} velocity={}",
            m.getUUID(),event,context,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(used.getItem()),m.getUsedItemHand(),started,m.level().getGameTime(),m.getTicksUsingItem(),m.getUseItemRemainingTicks(),ownsUse(m),m.isUsingItem(),m.isInWaterOrRain(),level(used),plan==null?0:plan.strength(),released,m.isAutoSpinAttack(),m.getDeltaMovement());}
}
