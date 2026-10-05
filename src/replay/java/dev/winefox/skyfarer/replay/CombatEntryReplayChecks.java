package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** External airborne handoffs reproduce low kinetic energy and targets above the current flight. */
public final class CombatEntryReplayChecks {
    public static void main(String[] args){run("low-energy-summit",10,8,.4,false);run("upward-entry",4,18,.7,true);}
    private static void run(String name,double height,double targetHeight,double speed,boolean requireUp){
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var world=new ReplayWorld();world.boxes.add(new FlightCorridor.Box(-.5,0,27.5,.5,targetHeight,28.5));
        var maid=new EntityMaid(world,new Vec3(0,height,0));maid.owner=new Player(world,Vec3.ZERO);
        maid.glide=true;maid.ground=false;maid.pose=Pose.FALL_FLYING;maid.velocity=new Vec3(0,requireUp?.2:-.1,speed);
        var target=new LivingEntity(world,new Vec3(0,targetHeight,28));
        var controller=new AirCombatController(maid);controller.acceptCommand(target);
        int hit=-1,landed=-1,slow=0,maxSlow=0;boolean upward=false;
        for(int tick=0;tick<800;tick++){
            world.advance(tick);controller.tick();
            var v=maid.velocity;var state=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.yaw,maid.pitch);
            boolean powered=world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time);
            if(controller.spearAttackActive()&&target.alive){
                upward|=maid.getEyeY()<target.getY()&&v.y>.05&&maid.pitch<0;
                slow=!powered&&v.horizontalDistance()<.45&&maid.distanceTo(target)>8?slow+1:0;maxSlow=Math.max(maxSlow,slow);
                var aim=new FlightPrediction.Aim(0,targetHeight-3,28,.3,7.8,0,0,0,0,2.25,.1,0);
                if(maid.using&&maid.usedTicks>=10&&v.length()>=.2&&aim.missSquared(state,0)<1e-8&&world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter())){
                    controller.recordSpearHit(target.id);target.alive=false;hit=tick;
                }
            }else slow=0;
            if(maid.using)maid.usedTicks++;
            Vec3 next;
            if(maid.glide){var s=FlightPrediction.step(state,maid.yaw,maid.pitch,180,90,90,.08,powered);next=new Vec3(s.x(),s.y()-maid.getEyeHeight(),s.z());maid.velocity=new Vec3(s.vx(),s.vy(),s.vz());}
            else if(!maid.ground){next=maid.pos.add(maid.velocity);maid.velocity=new Vec3(v.x*.91,(v.y-.08)*.98,v.z*.91);}else next=maid.pos;
            double ground=world.ground(next.x,next.z,next.y+2);
            if(Double.isFinite(ground)&&next.y<=ground){next=new Vec3(next.x,ground,next.z);maid.ground=true;maid.glide=false;maid.pose=Pose.STANDING;maid.velocity=Vec3.ZERO;}else maid.ground=false;
            maid.pos=next;for(var rocket:world.rockets)if(rocket.alive&&rocket.createdAt<world.time&&++rocket.life>rocket.lifetime)rocket.discard();
            if(hit>=0&&maid.ground&&controller.state()==AirCombatState.GROUNDED){landed=tick;break;}
        }
        try{java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/controller-replay"));java.nio.file.Files.write(java.nio.file.Path.of("build/reports/controller-replay/"+name+".events.log"),ReplayWorld.events);}catch(java.io.IOException e){throw new RuntimeException(e);}
        if(hit<0||landed<0||requireUp&&!upward||maxSlow>24)throw new AssertionError(name+" hit="+hit+" landed="+landed+" upward="+upward+" slowTicks="+maxSlow);
        System.out.println(name+" hit="+hit+" landed="+landed+" upward="+upward+" maxSlowTicks="+maxSlow+" rockets="+maid.inventory.consumed);
    }
}
