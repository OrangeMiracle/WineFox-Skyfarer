package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class LaunchAttackReplayChecks {
    public static void main(String[] args)throws Exception{run(true);run(false);}
    private static void run(boolean opportunity)throws Exception{
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var world=new ReplayWorld();var maid=new EntityMaid(world,Vec3.ZERO);maid.owner=new Player(world,Vec3.ZERO);maid.inventory.duration=0;maid.yaw=180;
        var locked=new LivingEntity(world,new Vec3(-10,0,0));
        var uphill=opportunity?new LivingEntity(world,new Vec3(0,6,10)):null;
        if(opportunity){
            require(TargetEvaluator.launchTargets(maid,locked,null,Vec3.ZERO).contains(uphill),"legal alternative absent");
            CommandAccess.targets=m->java.util.List.of(locked);
            require(!TargetEvaluator.launchTargets(maid,locked,null,Vec3.ZERO).contains(uphill),"opportunity overrode marked target");
            CommandAccess.targets=m->java.util.List.of();uphill.hostile=false;
            require(!TargetEvaluator.launchTargets(maid,locked,null,Vec3.ZERO).contains(uphill),"friendly launch target accepted");uphill.hostile=true;
        }
        if(opportunity)for(int z=3;z<=10;z++)world.boxes.add(new FlightCorridor.Box(-1,0,z,1,(z-2)*.75,z+1));
        var controller=new AirCombatController(maid);controller.acceptCommand(locked);
        boolean launched=false,ordinary=false,hit=false,exit=false,exitComplete=false;int launchAt=-1;
        for(int tick=0;tick<220;tick++){
            world.advance(tick);controller.tick();
            launched|=ReplayWorld.events.stream().anyMatch(e->e.contains("reason=LAUNCH_ATTACK:"));
            if(launched&&launchAt<0)launchAt=tick;
            if(opportunity&&launched&&!hit)require(tick-launchAt<25&&!ReplayWorld.events.stream().anyMatch(e->e.contains("reason=CONTINUOUS_ATTACK:")),"Launch Attack did not hit during takeoff");
            ordinary|=controller.state()==AirCombatState.CLIMB;
            exit|=launched&&controller.state()==AirCombatState.ATTACK_EXIT;
            exitComplete|=hit&&exit&&controller.state()!=AirCombatState.ATTACK_EXIT;
            if(launched)require(uphill!=null&&uphill.id.equals(controller.debugTarget())||hit,"wrong launch target");
            var v=maid.velocity;var s=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.yaw,maid.pitch);
            if(uphill!=null&&uphill.alive&&controller.spearAttackActive()&&maid.using&&maid.usedTicks>=10){
                var aim=new FlightPrediction.Aim(uphill.getX(),uphill.getY()-3,uphill.getZ(),.3,7.8,0,0,0,0,2.25,.1,0);
                if(v.horizontalDistance()>=.2&&aim.missSquared(s,0)<1e-8&&world.ray(maid.getEyePosition(),uphill.getBoundingBox().getCenter())){
                    controller.recordSpearHit(uphill.id);uphill.alive=false;locked.alive=false;hit=true;
                }
            }
            if(maid.using)maid.usedTicks++;
            Vec3 next;
            if(maid.glide){
                boolean powered=world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time);
                var n=FlightPrediction.step(s,maid.yaw,maid.pitch,180,90,90,.08,powered);next=new Vec3(n.x(),n.y()-maid.getEyeHeight(),n.z());maid.velocity=new Vec3(n.vx(),n.vy(),n.vz());
                require(new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(s,n,0,0),"launch body hit terrain tick="+tick+" state="+controller.state());
            }else if(!maid.ground){next=maid.pos.add(v);maid.velocity=new Vec3(v.x*.91,(v.y-.08)*.98,v.z*.91);}else next=maid.pos;
            if(next.y<=0){next=new Vec3(next.x,0,next.z);maid.ground=true;maid.glide=false;maid.pose=Pose.STANDING;maid.velocity=Vec3.ZERO;}else maid.ground=false;
            maid.pos=next;for(var r:world.rockets)if(r.alive&&r.createdAt<world.time&&++r.life>r.lifetime)r.discard();
            if(opportunity&&hit&&exitComplete||!opportunity&&ordinary)break;
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/controller-replay"));
        java.nio.file.Files.write(java.nio.file.Path.of("build/reports/controller-replay/launch-"+opportunity+".events.log"),ReplayWorld.events);
        controller.dispose("TEST_END",true);PlannerSession.stop();
        require(opportunity?launched&&hit&&exitComplete:!launched&&ordinary,"launch result opportunity="+opportunity+" launch="+launched+" hit="+hit+" exitComplete="+exitComplete+" ordinary="+ordinary);
        System.out.println("Launch replay opportunity="+opportunity+" launch="+launched+" nativeHit="+hit+" exit="+exit+" normalFallback="+ordinary);
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
