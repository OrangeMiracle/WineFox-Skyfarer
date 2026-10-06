package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FunnelAsyncChecks {
    private static final CollisionSnapshot.Cell AIR=new CollisionSnapshot.Cell(List.of(),List.of(),0,true);
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static PlanningTask.Funnel task(double x,PhysicsContext physics){
        var aim=new FlightPrediction.Aim(x,9,10,.3,7.8,0,0,0,0,2.25,.1,10);
        var input=new AttackFunnelPlanner.Input(new SweepingAttackPlanner.Point(x,13.4,10),aim,physics,5,.3,8);
        return new PlanningTask.Funnel(new FlightPrediction.State(x,13.4,10,0,0,0,0,0,physics),input);
    }
    private static CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells){return new CollisionSnapshot(cells,.6,.6,.4,false,-128,-128,128,128);}
    private static void pending(PlannerSession session,PlanningTask task){try{session.query(task);throw new AssertionError("stale funnel returned synchronously");}catch(PlannerSession.Pending expected){}}
    public static void main(String[] args)throws Exception{
        final Thread owner=Thread.currentThread();final Object[] level={new Object()};final String[] dimension={"a"};
        final PhysicsContext[] physics={PhysicsContext.VANILLA};
        var source=new PlannerSession.Source(){
            public CollisionSnapshot.Cell capture(long key){require(Thread.currentThread()==owner,"world capture on worker");return AIR;}
            public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){require(Thread.currentThread()==owner,"snapshot off owner");return FunnelAsyncChecks.snapshot(cells);}
            public Object levelIdentity(){return level[0];}public String dimension(){return dimension[0];}public PhysicsContext physics(long tick){return physics[0];}
            public int lastReads(){return 0;}
        };
        var cold=PlannerExecutor.submit(task(1,physics[0]),snapshot(Map.of()),1);
        long deadline=System.nanoTime()+5_000_000_000L;
        while(cold.outcome==null&&System.nanoTime()<deadline)Thread.sleep(5);
        require(cold.outcome!=null&&cold.outcome.failure()==null&&!cold.outcome.missing().isEmpty(),"cold discovery failed");
        require(((PlanningTask.FunnelResult)cold.outcome.value()).atlas().windows().isEmpty(),"unknown space became an executable funnel");
        var session=new PlannerSession(source);PlanningTask.FunnelResult result=null;int tick=0;
        for(;tick<70&&result==null;tick++){
            PlannerSession.serverTick();session.begin(tick,"map","target",1,1);
            try{var candidate=(PlanningTask.FunnelResult)session.query(task(1,physics[0]));if(!candidate.atlas().windows().isEmpty())result=candidate;}catch(PlannerSession.Pending expected){}
            Thread.sleep(25);
        }
        require(result!=null,"bounded cold map never became ready");
        require(result.coverage().size()<=640&&session.cellCount()<=768&&session.pendingCount()<=24,"funnel escaped cache bounds");
        require(session.coverageValid(result),"fresh selected snapshot coverage invalid");
        var original=task(1,physics[0]);
        var farPose=new PlanningTask.Funnel(new FlightPrediction.State(90,60,-80,2,.5,-1,170,30,physics[0]),original.input());
        require(session.query(farPose)==result,"maid movement rebuilt target-centred windows");
        long key=result.coverage().keySet().iterator().next();
        var changed=new PlanningTask.FunnelResult(result.atlas(),Map.of(key,new CollisionSnapshot.Cell(List.of(),List.of(),0,false)));
        require(!session.coverageValid(changed),"changed loaded/shape data retained old proof");
        var absent=new PlanningTask.FunnelResult(result.atlas(),Map.of(CollisionSnapshot.key(200,200,200),AIR));
        require(!session.coverageValid(absent),"evicted/missing proof coverage accepted");
        require(!task(3,physics[0]).compatible(original),"significant movement retained target map compatibility");
        session.begin(tick+=41,"map","target",1,1);pending(session,original);
        pending(session,task(3,physics[0])); // significant target movement, same UUID
        long generation=session.generation();
        session.begin(++tick,"map","different-target",1,1);
        require(session.generation()>generation&&session.pendingCount()==0,"old target job survived invalidation");
        Thread.sleep(60);pending(session,task(1,physics[0]));
        generation=session.generation();level[0]=new Object();session.begin(++tick,"map","different-target",1,1);
        require(session.generation()>generation&&session.cellCount()==0,"same dimension new Level retained funnel terrain");
        pending(session,task(1,physics[0]));generation=session.generation();dimension[0]="b";session.begin(++tick,"map","different-target",1,1);
        require(session.generation()>generation,"dimension change kept old windows");
        pending(session,task(1,physics[0]));generation=session.generation();physics[0]=new PhysicsContext(.04,0);session.begin(++tick,"map","different-target",1,1);
        require(session.generation()>generation,"physics change kept windows");
        pending(session,task(1,physics[0]));generation=session.generation();session.begin(++tick,"map","different-target",2,1);
        require(session.generation()>generation,"configuration change kept windows");
        session.close();require(session.pendingCount()==0&&session.cellCount()==0,"closed atlas retained resources");
        earlyGroundWindow(false);earlyGroundWindow(true);PlannerSession.stop();
        System.out.println("Funnel async: bounded cold map, cache/age/context invalidation, cancelled mailbox, ground prewarm and real controller coast/pulse Capture-Rejoin-hit passed.");
    }
    private static void earlyGroundWindow(boolean needy)throws Exception{
        var world=new dev.winefox.skyfarer.replay.ReplayWorld();
        var maid=new com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid(world,new net.minecraft.world.phys.Vec3(0,0,-10));
        maid.owner=new net.minecraft.world.entity.player.Player(world,new net.minecraft.world.phys.Vec3(0,0,0));
        var target=new net.minecraft.world.entity.LivingEntity(world,new net.minecraft.world.phys.Vec3(1,12,10));
        var controller=new dev.winefox.skyfarer.ai.AirCombatController(maid);boolean found=false;
        for(int tick=0;tick<70;tick++){
            world.advance(tick);controller.prewarmCommand(target);
            var tubes=controller.debugSnapshot().getList("attack_funnels",10);
            if(!tubes.isEmpty()){found=true;break;}
        }
        require(found&&!maid.glide&&!controller.controlsAir()&&world.rockets.isEmpty(),"early target map required flight or took over transit/inventory");
        // Read an actually completed server window, then start outside its spatial gate.
        var field=controller.getClass().getDeclaredField("funnelAtlas");field.setAccessible(true);
        var atlas=(AttackFunnelPlanner.Atlas)field.get(controller);var window=atlas.windows().getFirst();var entry=window.witness();
        var tangent=window.path().entry.b().subtract(window.path().entry.a()).unit();
        maid.pose=net.minecraft.world.entity.Pose.FALL_FLYING;maid.glide=true;maid.ground=false;
        double back=needy?30:6;
        maid.pos=new net.minecraft.world.phys.Vec3(entry.x()-tangent.x()*back,entry.y()-.4,entry.z()-tangent.z()*back);
        maid.velocity=needy?new net.minecraft.world.phys.Vec3(tangent.x()*.15,-.05,tangent.z()*.15):new net.minecraft.world.phys.Vec3(entry.vx(),entry.vy(),entry.vz());
        maid.inventory.duration=0;maid.yaw=(float)(entry.yaw()+(needy?0:12));maid.pitch=(float)entry.pitch();
        dev.winefox.skyfarer.config.SkyfarerSettings.setDebugLogging(true);
        dev.winefox.skyfarer.replay.ReplayWorld.events.clear();controller.acceptCommand(target);
        boolean capture=false,commit=false,hit=false;int startTick=(int)world.time+1;
        for(int tick=startTick;tick<startTick+100;tick++){
            world.advance(tick);controller.tick();
            var debug=controller.debugSnapshot();capture|=debug.getString("reason").startsWith("FUNNEL_");
            commit|=dev.winefox.skyfarer.replay.ReplayWorld.events.stream().anyMatch(e->e.contains("reason=FUNNEL_CAPTURE:"));
            var v=maid.velocity;var state=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.yaw,maid.pitch,PhysicsContext.VANILLA);
            var aim=new FlightPrediction.Aim(1,9,10,.3,7.8,0,0,0,0,2.25,.1,0);
            if(controller.spearAttackActive()&&maid.using&&maid.usedTicks>=10&&aim.missSquared(state,0)<1e-8&&world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter())){hit=true;break;}
            if(maid.using)maid.usedTicks++;
            boolean powered=world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time);
            var next=FlightPrediction.step(state,maid.yaw,maid.pitch,180,90,90,.08,powered);
            require(new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(maid.getEyePosition(),new net.minecraft.world.phys.Vec3(next.x(),next.y(),next.z()),0,0),"Capture replay hit terrain");
            maid.pos=new net.minecraft.world.phys.Vec3(next.x(),next.y()-.4,next.z());maid.velocity=new net.minecraft.world.phys.Vec3(next.vx(),next.vy(),next.vz());
            for(var r:world.rockets)if(r.alive&&r.createdAt<world.time&&++r.life>r.lifetime)r.discard();
        }
        require(capture&&commit&&hit,"actual controller did not capture/rejoin/hit the early window: needy="+needy+" capture="+capture+" commit="+commit+" hit="+hit+" state="+controller.state());
        require(needy?maid.inventory.consumed>=1:maid.inventory.consumed==0,"capture inventory action disagrees with coast/pulse certificate");
        controller.release();
    }
}
