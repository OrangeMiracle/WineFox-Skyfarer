package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.replay.ReplayWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class HandoffStabilityChecks {
    public static void main(String[] args){
        var world=new ReplayWorld();var maid=new EntityMaid(world,new Vec3(0,80,0));maid.owner=new Player(world,Vec3.ZERO);
        maid.ground=false;maid.glide=true;maid.pose=Pose.FALL_FLYING;maid.velocity=new Vec3(0,-1.2,1.2);
        var target=new LivingEntity(world,new Vec3(0,65,24));
        var transit=new TransitFlight();
        // Exact crash ordering: new order -> debug watch before the first entity tick.
        transit.debug(new CompoundTag(),maid);transit.close();transit.debug(new CompoundTag(),maid);
        var controller=new AirCombatController(maid);transit.handoff(maid);controller.acceptCommand(target);
        require(maid.isFallFlying()&&controller.controlsAir(),"high-altitude handoff lost flight");
        controller.release("ORDER_REPLACED");require(maid.isFallFlying()&&controller.controlsAir(),"order release caused freefall");
        controller.handoffToTransit();require(maid.isFallFlying()&&!controller.controlsAir(),"handoff revoked wings");
        controller.acceptCommand(target);maid.glide=false;world.advance(1);controller.tick();
        require(maid.isFallFlying()&&controller.controlsAir(),"external flag loss was not restored");
        controller.dispose("ENTITY_LEAVE",true);require(maid.isFallFlying(),"unload cleared wings");
        var replacement=new AirCombatController(maid);world.advance(2);replacement.tick();
        require(replacement.controlsAir()&&maid.isFallFlying(),"reload did not reacquire legal flight");
        replacement.dispose("TEST_END",true);PlannerSession.stop();
        System.out.println("Transit debug-before-tick/after-close and airborne lifecycle checks passed");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
