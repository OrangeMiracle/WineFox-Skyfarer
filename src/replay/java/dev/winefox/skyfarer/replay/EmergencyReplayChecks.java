package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class EmergencyReplayChecks {
    public static void main(String[] args)throws Exception{
        run(14,true);run(20,false);
    }
    private static void run(double wallZ,boolean allowsBoost)throws Exception{
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var world=new ReplayWorld();world.boxes.add(new FlightCorridor.Box(-100,-20,wallZ,100,200,wallZ+1));
        var maid=new EntityMaid(world,new Vec3(0,30,0));maid.owner=new Player(world,Vec3.ZERO);maid.inventory.duration=0;
        maid.glide=true;maid.ground=false;maid.pose=Pose.FALL_FLYING;maid.velocity=new Vec3(0,0,3);maid.yaw=90;
        var target=new LivingEntity(world,new Vec3(-20,30,5));var controller=new AirCombatController(maid);controller.acceptCommand(target);
        // Earlier live steering can make glide sufficient before the worker arrives.
        // StabilityLaunchChecks separately retains the coast-impossible / funded-safe case.
        boolean collision=false;int detection=-1,boost=-1,certified=-1;
        for(int tick=0;tick<50;tick++){
            world.advance(tick);controller.tick();
            if(detection<0&&ReplayWorld.events.stream().anyMatch(e->e.contains("EMERGENCY_DETECTED")))detection=tick;
            if(boost<0&&ReplayWorld.events.stream().anyMatch(e->e.contains("reason=EMERGENCY_BOOST_TURN")))boost=tick;
            if(certified<0&&ReplayWorld.events.stream().anyMatch(e->e.contains("action=EMERGENCY_CERTIFIED")))certified=tick;
            var v=maid.velocity;var s=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.yaw,maid.pitch);
            var n=FlightPrediction.step(s,maid.yaw,maid.pitch,180,90,90,.08,world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time));
            collision|=!new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(s,n,0,0);
            maid.pos=new Vec3(n.x(),n.y()-maid.getEyeHeight(),n.z());maid.velocity=new Vec3(n.vx(),n.vy(),n.vz());
            for(var r:world.rockets)if(r.alive&&r.createdAt<world.time&&++r.life>r.lifetime)r.discard();
            if(collision)break;
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/controller-replay"));
        java.nio.file.Files.write(java.nio.file.Path.of("build/reports/controller-replay/emergency-"+(int)wallZ+".events.log"),ReplayWorld.events);
        controller.dispose("TEST_END",true);PlannerSession.stop();
        if(collision||detection!=0||certified<0||certified>14||maid.velocity.z>=0||maid.getZ()>=wallZ-1
            ||maid.inventory.consumed!=(boost<0?0:1)||!allowsBoost&&boost>=0)
            throw new AssertionError("emergency collision="+collision+" detected="+detection+" certified="+certified+" boost="+boost+" consumed="+maid.inventory.consumed+" position="+maid.pos+" velocity="+maid.velocity);
        System.out.println("Cold async emergency wall="+wallZ+" detected="+detection+" boost="+boost+" collision=false; complete escape, inventory pulses="+maid.inventory.consumed);
    }
}
