package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Locked target disappears behind a mountain shoulder. Real controller, physics and contact ray. */
public final class SweepingAttackReplayChecks {
    public static void main(String[] args){run(1);run(-1);}
    private static void run(int mirror){
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var world=new ReplayWorld();
        box(world,mirror,-40,0,-40,0,28,40);box(world,mirror,-10,0,-16,8,24,-8);box(world,mirror,0,0,9.5,1.5,12,10.5);
        var maid=new EntityMaid(world,new Vec3(mirror*9,13.6,-28));maid.owner=new Player(world,new Vec3(mirror*20,0,-28));
        maid.glide=true;maid.ground=false;maid.pose=Pose.FALL_FLYING;maid.yaw=-35*mirror;
        maid.velocity=new Vec3(mirror*Math.sin(Math.toRadians(35))*1.3,-.05,Math.cos(Math.toRadians(35))*1.3);
        var target=new LivingEntity(world,new Vec3(mirror,12,10));
        if(world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter()))throw new AssertionError("Fixture must hide the acquired target");
        var controller=new AirCombatController(maid);controller.acceptCommand(target);
        int hit=-1,landed=-1;boolean curve=false,exit=false;String failure="";
        var trace=new java.util.ArrayList<String>();
        for(int tick=0;tick<800;tick++){
            world.time=tick;controller.tick();
            var debug=controller.debugSnapshot();curve|=!debug.getString("curve_style").isEmpty();
            exit|=debug.getString("reason").contains("CONTINUOUS_")&&controller.state()==AirCombatState.ATTACK_EXIT;
            var v=maid.velocity;var s=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.yaw,maid.pitch);
            if(controller.spearAttackActive()&&target.alive&&maid.using&&maid.usedTicks>=10){
                var aim=new FlightPrediction.Aim(mirror,9,10,.3,7.8,0,0,0,0,2.25,.1,0);
                if(v.length()>=.2&&aim.missSquared(s,0)<1e-8&&world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter())){
                    controller.recordSpearHit(target.id);target.alive=false;hit=tick;
                }
            }
            if(maid.using)maid.usedTicks++;
            boolean powered=world.rockets.stream().anyMatch(r->r.alive);Vec3 next;
            if(maid.glide){var moved=FlightPrediction.step(s,maid.yaw,maid.pitch,180,90,90,.08,powered);next=new Vec3(moved.x(),moved.y()-maid.getEyeHeight(),moved.z());maid.velocity=new Vec3(moved.vx(),moved.vy(),moved.vz());}
            else if(!maid.ground){next=maid.pos.add(v);maid.velocity=new Vec3(v.x*.91,(v.y-.08)*.98,v.z*.91);}else next=maid.pos;
            if(maid.glide&&controller.state()!=AirCombatState.LANDING&&!new CorridorProbe(maid).clear(maid.getEyePosition(),next.add(0,maid.getEyeHeight(),0),0,0)){failure="Body collision at "+tick+" state="+controller.state();break;}
            double ground=world.ground(next.x,next.z,Math.max(maid.pos.y,next.y)+.1);
            if(Double.isFinite(ground)&&next.y<=ground){next=new Vec3(next.x,ground,next.z);maid.ground=true;maid.glide=false;maid.velocity=Vec3.ZERO;}else maid.ground=false;
            maid.pos=next;for(var r:world.rockets)if(r.alive&&++r.life>r.lifetime)r.discard();
            trace.add(tick+"\t"+controller.state()+"\t"+next+"\t"+maid.yaw+"\t"+debug.getString("reason"));
            if(hit>=0&&maid.ground&&controller.state()==AirCombatState.GROUNDED){landed=tick;break;}
        }
        try{var dir=java.nio.file.Path.of("build/reports/controller-replay");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("sweeping-hill-"+mirror+".tsv"),trace);java.nio.file.Files.write(dir.resolve("sweeping-hill-"+mirror+".events.log"),ReplayWorld.events);}catch(java.io.IOException e){throw new RuntimeException(e);}
        if(!failure.isEmpty()||hit<0||landed<0||!curve||!exit)throw new AssertionError("hill mirror="+mirror+" hit="+hit+" landed="+landed+" curve="+curve+" exit="+exit+" "+failure);
        System.out.println("Occluded hillside mirror="+mirror+" curved hit="+hit+" continued exit="+exit+" landed="+landed);
    }
    private static void box(ReplayWorld w,int m,double x,double y,double z,double xx,double yy,double zz){w.boxes.add(new FlightCorridor.Box(Math.min(x*m,xx*m),y,z,Math.max(x*m,xx*m),yy,zz));}
}
