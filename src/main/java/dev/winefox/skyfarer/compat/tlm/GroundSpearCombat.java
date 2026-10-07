package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import dev.winefox.skyfarer.compat.spears.GroundMeleeReach;

/** TLM's ordinary left-click attack path, gated by the grounded equipment fallback. */
public final class GroundSpearCombat {
    private GroundSpearCombat() {}
    public static void tick(EntityMaid maid) {
        if(!maid.onGround()||maid.isFallFlying()||!SkyfarerEquipment.spear(maid))return;
        var brain=maid.getBrain();
        var previous=brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        var selected=TargetEvaluator.scan(maid,previous,previous==null?null:previous.getUUID(),CombatAnchor.current(maid)).selection();
        var target=selected==null?null:selected.target();
        if(target==null){if(previous!=null)TlmFollowCompat.clearGroundMovement(maid);brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);return;}
        maid.stopUsingItem();
        brain.setMemory(MemoryModuleType.ATTACK_TARGET,target);
        brain.setMemory(MemoryModuleType.LOOK_TARGET,new EntityTracker(target,true));
        // Mob's native spear left-click is a look ray; a deferred LOOK_TARGET alone is not an aim.
        maid.lookAt(target,180,180);maid.setYRot(maid.getYHeadRot());
        boolean contact=SpearCompat.groundContact(maid,target);
        double minimum=SpearCompat.groundMinimum(maid.getMainHandItem());
        double spacing=GroundMeleeReach.spacing(minimum,SpearCompat.groundMaximum(maid));
        var away=maid.position().subtract(target.position()).multiply(1,0,1);
        // Keep retreating while stabbing if an approaching mob has entered our preferred spacing.
        // Navigation still owns terrain/path safety; no velocity shove or teleport.
        if(minimum>0&&away.length()<spacing-.25) {
            if(away.lengthSqr()<.001)away=new net.minecraft.world.phys.Vec3(1,0,0);
            walk(maid,new WalkTarget(target.position().add(away.normalize().scale(spacing)),.6f,0));
        } else if(!contact) {
            walk(maid,new WalkTarget(new EntityTracker(target,false),.6f,0));
        } else if(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {maid.getNavigation().stop();brain.eraseMemory(MemoryModuleType.WALK_TARGET);}
        if(!contact)return;
        if(!brain.hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)&&maid.hasLineOfSight(target)) {
            maid.swing(InteractionHand.MAIN_HAND);maid.doHurtTarget(target);
            double speed=maid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN,true,Math.max(1,(long)(20/(speed>0?speed:1))));
        }
    }
    private static void walk(EntityMaid maid,WalkTarget desired) {
        var current=maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if(current!=null) {
            if(current.getTarget() instanceof EntityTracker a&&desired.getTarget() instanceof EntityTracker b&&a.getEntity()==b.getEntity())return;
            if(!(current.getTarget() instanceof EntityTracker)&&!(desired.getTarget() instanceof EntityTracker)
                &&current.getTarget().currentPosition().distanceTo(desired.getTarget().currentPosition())<.5)return;
        }
        // EntityTracker follows moving targets itself. Keep MoveToTargetSink's current path/node alive.
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,desired);
    }
}
