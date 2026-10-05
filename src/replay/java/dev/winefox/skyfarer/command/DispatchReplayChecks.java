package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.replay.ReplayWorld;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Production transit -> production combat -> landing, with external physics/world fixtures. */
public final class DispatchReplayChecks {
    public static void main(String[] args) {
        run(180,0,0,false);run(900,180,11,true);run(256,-90,5,false);run(180,0,5,false,true);
        run(900,0,5,false,false,2);run(900,180,11,false,false,100);run(180,90,0,false,true,2);
        CommandAccess.targets=m->List.of();CommandAccess.anchor=m->null;CommandAccess.designated=(m,t)->false;
        System.out.println("Dispatch full-cycle replay passed; transit consumes no inventory; combat and landing remain native controller decisions.");
    }
    private static void run(double distance,float yaw,int jitter,boolean snow) {
        run(distance,yaw,jitter,snow,false);
    }
    private static void run(double distance,float yaw,int jitter,boolean snow,boolean friendly) {
        run(distance,yaw,jitter,snow,friendly,0);
    }
    private static void run(double distance,float yaw,int jitter,boolean snow,boolean friendly,double creativeSpeed) {
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();var world=new ReplayWorld();world.loadedRadius=2048;world.rocketJitter=jitter;
        if(creativeSpeed>0)SkyfarerSettings.set("creative_transit_speed_multiplier",creativeSpeed);
        world.boxes.clear();world.boxes.add(new FlightCorridor.Box(-2048,-20,-2048,2048,snow?.125:0,2048));
        var maid=new EntityMaid(world,new Vec3(0,snow?.125:0,0));maid.owner=new Player(world,maid.pos);maid.yaw=yaw;maid.home=true;
        var target=new LivingEntity(world,new Vec3(0,maid.getY(),distance));
        target.hostile=!friendly;CommandAccess.designated=(m,t)->m==maid&&t==target&&target.alive;
        CommandAccess.targets=m->target.alive?List.of(target):List.of();CommandAccess.anchor=m->target.pos;
        var transit=new TransitFlight();var controller=new AirCombatController(maid);
        int handoff=-1,hit=-1,landed=-1;boolean exit=false;
        for(int tick=0;tick<2200;tick++) {
            world.advance(tick);
            if(handoff<0&&maid.distanceTo(target)<=DispatchPolicy.handoffRange(creativeSpeed>0)) {
                transit.handoff(maid);handoff=tick;controller.acceptCommand(target);
                require(maid.velocity.length()<=1.7001,"creative speed leaked into combat");
                require(maid.inventory.consumed==0,"dispatch consumed inventory");
                require(world.rockets.stream().noneMatch(r->r.alive),"free rocket survived combat handoff");
            }
            if(handoff<0)require(transit.tick(maid,target.pos,creativeSpeed>0),"transit abandoned open route at tick "+tick+" pos="+maid.pos);
            else controller.tick();
            if(controller.state()==AirCombatState.ATTACK_EXIT)exit=true;
            if(target.alive&&controller.spearAttackActive()&&maid.using&&maid.usedTicks>=10) {
                var aim=new FlightPrediction.Aim(target.getX(),target.getY()-3,target.getZ(),.3,target.getBbHeight()+6,0,0,0,0,2.25,.1,0);
                if(maid.velocity.length()>=.2&&aim.missSquared(state(maid),0)<1e-8&&world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter())) {
                    controller.recordSpearHit(target.id);target.alive=false;hit=tick;
                }
            }
            if(maid.using)maid.usedTicks++;
            var before=state(maid);Vec3 next;
            if(maid.glide) {
                var s=FlightPrediction.step(before,maid.yaw,maid.pitch,180,90,90,.08,world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time));
                next=new Vec3(s.x(),s.y()-maid.getEyeHeight(),s.z());maid.velocity=new Vec3(s.vx(),s.vy(),s.vz());
            } else if(!maid.ground){next=maid.pos.add(maid.velocity);maid.velocity=new Vec3(maid.velocity.x*.91,(maid.velocity.y-.08)*.98,maid.velocity.z*.91);}
            else next=maid.pos;
            double floor=snow?.125:0;
            if(next.y<=floor){
                if(maid.glide&&controller.state()!=AirCombatState.LANDING){
                    try{java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/controller-replay"));
                        java.nio.file.Files.write(java.nio.file.Path.of("build/reports/controller-replay/dispatch-failure.events.log"),ReplayWorld.events);
                    }catch(java.io.IOException e){throw new RuntimeException(e);}
                    throw new AssertionError("unplanned ground contact in "+controller.state()+" tick="+tick+" speed="+creativeSpeed+" before="+before+" next="+next);
                }
                next=new Vec3(next.x,floor,next.z);maid.ground=true;maid.glide=false;maid.pose=Pose.STANDING;maid.velocity=Vec3.ZERO;
            }
            else maid.ground=false;
            maid.pos=next;for(var r:world.rockets)if(r.alive&&r.createdAt<world.time&&++r.life>r.lifetime)r.discard();
            if(handoff<0)require(maid.getY()<(creativeSpeed>0?215:35),"unbounded transit ascent tick="+tick+" pos="+maid.pos+" pitch="+maid.pitch+" v="+maid.velocity+" details="+transit.diagnostics());
            if(hit>=0&&maid.ground&&controller.state()==AirCombatState.GROUNDED){landed=tick;break;}
        }
        transit.close();exit|=ReplayWorld.events.stream().anyMatch(s->s.contains(" -> ATTACK_EXIT;"));
        require(handoff>0&&hit>handoff&&exit&&landed>hit,"incomplete dispatch: "+handoff+"/"+hit+"/"+landed+" exit="+exit);
        System.out.printf(Locale.ROOT,"dispatch %.0f blocks yaw=%.0f snow=%s friendly=%s creativeMultiplier=%.0f handoff=%d hit=%d landed=%d combat rockets=%d%n",distance,yaw,snow,friendly,creativeSpeed,handoff,hit,landed,maid.inventory.consumed);
    }
    private static FlightPrediction.State state(EntityMaid m){return new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.velocity.x,m.velocity.y,m.velocity.z,m.yaw,m.pitch);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
