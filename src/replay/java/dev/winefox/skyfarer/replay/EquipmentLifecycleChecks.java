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
        var world=new ReplayWorld();world.loadedRadius=256;var m=new EntityMaid(world,new Vec3(0,80,0));m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.rain=true;
        m.owner=new Player(world,new Vec3(25,80,35));
        m.velocity=new Vec3(0,-.1,.7);m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var planning=new AsyncPlanning(m);var manager=new RiptidePropulsion();var heading=new HeadingController();
        require(RiptidePropulsion.available(m)==3,"rain activation denied");m.rain=false;
        require(RiptidePropulsion.available(m)==0,"dry/roofed activation allowed");m.water=true;require(RiptidePropulsion.available(m)==3,"water denied");m.water=false;m.rain=true;
        RiptidePlanner.Plan plan=null;long clock=10;
        // Warm actual async snapshots, holding the fixture at the decision point.
        for(;clock<180;clock++){world.advance(clock);planning.begin(clock,"RIPTIDE_TEST",null,0);var s=state(m);plan=planning.riptide(s,0,85,120,3,false);heading.beginTick(m);if(plan!=null&&manager.begin(m,plan,s,planning,heading))break;}
        require(manager.active(),"certified Riptide could not begin");
        var before=m.velocity;m.rain=false;
        require(!manager.tick(m,state(m),planning,heading)&&m.offhand.damage==0&&m.velocity==before&&!m.using,"drying during windup consumed durability or fired");
        require(!manager.ready(m),"dry environment became usable");
        m.rain=true;require(manager.ready(m),"artificial cancel cooldown still present");
        m.rain=true;clock+=21;world.advance(clock);planning.begin(clock,"RIPTIDE_TEST",null,0);heading.beginTick(m);
        require(manager.ready(m),"weather/cooldown did not recover eligibility");
        require(!manager.begin(m,plan,state(m),planning,heading),"expired snapshot reused for ignition");
        long deadline=clock+140;
        for(;clock<deadline;clock++){world.advance(clock);planning.begin(clock,"RIPTIDE_TEST",null,0);var s=state(m);plan=planning.riptide(s,0,85,120,3,false);heading.beginTick(m);if(plan!=null&&manager.begin(m,plan,s,planning,heading))break;}
        require(manager.active(),"could not retry after weather/cooldown/snapshot recovery");long began=clock;
        for(int t=1;t<=10;t++) {
            // Apply the previous tick's native gliding physics.
            var next=FlightPrediction.step(state(m),m.yaw,m.pitch,180,90,90,.08,false);move(m,next);m.usedTicks++;
            world.advance(began+t);planning.begin(began+t,"RIPTIDE_TEST",null,0);heading.beginTick(m);
            require(manager.tick(m,state(m),planning,heading),"windup/release aborted at "+t);
        }
        require(m.offhand.damage==1&&!m.using&&m.spinTicks==20&&m.velocity.length()>2,"release did not add native impulse/spin/wear");
        manager.cancel(m);require(m.glide,"propulsion cancel cleared glide");planning.close();
    }
    static FlightPrediction.State state(EntityMaid m){return new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.velocity.x,m.velocity.y,m.velocity.z,m.yaw,m.pitch,PhysicsContext.VANILLA);}
    static void move(EntityMaid m,FlightPrediction.State s){m.pos=new Vec3(s.x(),s.y()-m.getEyeHeight(),s.z());m.velocity=new Vec3(s.vx(),s.vy(),s.vz());}
}
