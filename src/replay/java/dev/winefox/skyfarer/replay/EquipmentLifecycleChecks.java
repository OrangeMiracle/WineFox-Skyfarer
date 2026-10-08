package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.RiptidePropulsion;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class EquipmentLifecycleChecks {
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args) {
        WingInventoryReplayChecks.run();
        System.setProperty("wfs.replayTickMillis","5");
        var world=new ReplayWorld();var maid=new EntityMaid(world,Vec3.ZERO);maid.owner=new Player(world,new Vec3(0,0,-10));
        var controller=new AirCombatController(maid);maid.wingDurability=19;world.advance(1);controller.tick();
        require(controller.state()==AirCombatState.GROUNDED&&!maid.glide&&maid.groundAttacks==1,"low wing launched instead of ground fallback");
        world.advance(2);controller.tick();require(maid.reports.size()<=1,"equipment bubble spam");
        maid.spareWings=1;world.advance(3);controller.tick();require(maid.wingDurability==432&&maid.spareWings==0,"spare not replaced");
        maid.pos=new Vec3(0,80,0);maid.ground=false;maid.glide=true;maid.pose=Pose.FALL_FLYING;maid.velocity=new Vec3(0,-.4,.7);
        var target=new LivingEntity(world,new Vec3(0,70,40));controller.acceptCommand(target);maid.wingDurability=19;maid.spareWings=1;world.advance(4);controller.tick();
        require(maid.glide&&maid.wingDurability==432&&maid.spareWings==0,"airborne spare swap failed");
        maid.wingDurability=19;world.advance(5);controller.tick();
        require(controller.controlsAir()&&maid.glide&&controller.state()==AirCombatState.RETURN_TO_OWNER,"low durability caused freefall or continued attack");
        maid.ground=true;maid.pos=Vec3.ZERO;world.advance(6);controller.tick();
        require(!controller.controlsAir()&&maid.groundAttacks>=3,"landing did not enter ground combat");
        controller.dispose("TEST_END",false);
        riptide();PlannerSession.stop();
        System.out.println("Actual controller low-wing ground/air/land/spare lifecycle and actual Riptide item-use lifecycle passed");
    }
    static void riptide() {
        RiptideReleaseChecks.resources();RiptideReleaseChecks.release(false);RiptideReleaseChecks.release(true);
    }
    static FlightPrediction.State state(EntityMaid m){return new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.velocity.x,m.velocity.y,m.velocity.z,m.yaw,m.pitch,PhysicsContext.VANILLA);}
    static void move(EntityMaid m,FlightPrediction.State s){m.pos=new Vec3(s.x(),s.y()-m.getEyeHeight(),s.z());m.velocity=new Vec3(s.vx(),s.vy(),s.vz());}
}
