package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Production controllers, physics and bounded worker pools; only game APIs are fixtures. */
public final class TrajectoryContinuityChecks {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        if(args.length==0||args[0].equals("contracts")){contracts();ownership();}
        if(args.length==0||args[0].equals("replay")){
            replay("dual-static",20,false,false);
            replay("dual-moving",20,true,false);
            replay("worker-blackout",5,false,true);
            replay("obstacle-blackout",5,false,true);
        }
        PlannerSession.stop();
    }
    static void contracts(){
        var start=new FlightPrediction.State(0,12,-30,0,-.08,1.1,0,0,PhysicsContext.VANILLA);
        var aim=new FlightPrediction.Aim(0,0,0,.3,7.8,0,0,0,0,2.25,.1,10);
        GlidePlanner.Corridor space=(a,b,h,v)->b.y()>.5&&Math.abs(b.x())<180&&Math.abs(b.z())<180;
        var selection=SweepingAttackPlanner.choose(start,new SweepingAttackPlanner.Point(0,2,0),aim,0,.08,5,.2,8,space,(s,a,t)->true);
        require(selection!=null,"contract fixture has no certified route");
        var commitment=new TrajectoryCommitment();commitment.select(selection.path(),100,"TEST");
        var proof=new TrajectoryCommitment.Proof(100,selection.path(),aim,0,.08,5,.2,false,selection.prediction());commitment.accept(proof);
        for(int age=0;age<=TrajectoryCommitment.MAX_PROOF_AGE;age++){
            var current=selection.prediction().trace().get(age).state();
            require(commitment.usable(100+age,current,aim.after(age),0,.08,5,.2,false)!=null,"certified suffix lost age="+age);
        }
        var exact=selection.prediction().trace().get(3).state();
        require(commitment.usable(103,exact.withPhysics(new PhysicsContext(.04,0)),aim.after(3),0,.08,5,.2,false)==null,"changed physics ignored");
        require(commitment.usable(103,exact,aim.after(3),5,.08,5,.2,false)==null,"changed propulsion ignored");
        var displaced=new FlightPrediction.State(exact.x()+2,exact.y(),exact.z(),exact.vx(),exact.vy(),exact.vz(),exact.yaw(),exact.pitch(),exact.physics());
        require(commitment.usable(103,displaced,aim.after(3),0,.08,5,.2,false)==null,"external displacement accepted");
        require(commitment.usable(107,selection.prediction().trace().get(7).state(),aim.after(7),0,.08,5,.2,false)==null,"proof lifetime extended by reads");
        commitment.accept(proof);
        require(commitment.usable(107,selection.prediction().trace().get(7).state(),aim.after(7),0,.08,5,.2,false)==null,"old mailbox arrival renewed certificate");
        var r=selection.prediction();
        var base=new SweepingAttackPlanner.Rollout(true,true,40,100,0,r.first(),r.trace());
        var marginal=new SweepingAttackPlanner.Rollout(true,true,39,95,0,r.first(),r.trace());
        var better=new SweepingAttackPlanner.Rollout(true,true,25,55,0,r.first(),r.trace());
        require(!commitment.better(130,base,marginal),"marginal candidate churned route");
        require(!commitment.better(105,base,better),"minimum commitment window ignored");
        require(commitment.better(130,base,better),"material improvement cannot switch");
        commitment.phaseChanged();require(commitment.usable(103,exact,aim.after(3),0,.08,5,.2,false)==null,"entry certificate leaked into exit");
        System.out.println("Trajectory contracts: time-aligned suffix, finite original timestamp, physics/force/drift rejection, switch cost and phase invalidation passed");
        var longStart=new FlightPrediction.State(-45,25,-10,-.536,-.342,-.613,165,37,PhysicsContext.VANILLA);
        var longAim=new FlightPrediction.Aim(0,-3,0,.3,7.8,0,0,0,0,2.25,.1,10);
        var longRoute=SweepingAttackPlanner.choose(longStart,new SweepingAttackPlanner.Point(0,2,0),longAim,0,.08,5,.2,8,space,(s,a,t)->true);
        require(longRoute!=null&&longRoute.prediction().contactTick()>80,"late-contact fixture no longer exercises the horizon mismatch");
        require(!SweepingAttackPlanner.simulate(longStart,longRoute.path(),longAim,0,.08,5,.2,false,space,(s,a,t)->true,80).safe(),"old 80-tick execution unexpectedly certifies late contact");
        require(SweepingAttackPlanner.simulate(longStart,longRoute.path(),longAim,0,.08,5,.2,false,space,(s,a,t)->true,120).safe(),"accepted long route lost on execution");
        System.out.println("Late-contact regression: contact="+longRoute.prediction().contactTick()+", old 80-tick follow rejects, full 120-tick follow certifies");
        var goal=new CompactTurnPlanner.Goal(-20,12,-15,0,-25,48,0,-50);
        var demand=new PropulsionDemand(PropulsionDemand.Phase.REPOSITION,goal.x(),goal.y(),goal.z(),.55).withTurn(goal,8);
        var turnPlan=RiptidePlanner.choose(start,demand,3,.08,space,SurfaceLaunchPlanner.DRY);
        require(turnPlan!=null,"compact-route propulsion fixture rejected");
        var actual=start;var requirement=demand;
        for(int i=1;i<turnPlan.trace().size();i++){
            requirement=requirement.advanceTurn(actual);
            actual=RiptidePlanner.step(actual,turnPlan.withDemand(requirement),RiptidePlanner.WINDUP+i-1,.08,false);
            require(actual.equals(turnPlan.trace().get(i)),"propulsion execution departed from certified compact route at "+i);
        }
        require(turnPlan.demand().turn().equals(goal),"future simulated turn leaked into current executor");
        require(RiptidePlanner.certify(start,turnPlan,RiptidePlanner.WINDUP,.08,(a,b,h,v)->false,SurfaceLaunchPlanner.DRY)==null,"turn demand bypassed collision");
        System.out.println("Compact propulsion: current goal retained, live stepping matches the full certified thrust/coast trace, collision veto passed");
    }
    static void ownership()throws Exception {
        var w=new ReplayWorld();var m=RiptideReleaseChecks.maid(w);var h=new HeadingController();h.beginTick(m);
        h.steerAngles(m,45,-20,8,8,HeadingController.Owner.PROPULSION);double yaw=m.yaw,pitch=m.pitch;
        h.steerAngles(m,-150,50,180,90);
        require(m.yaw==yaw&&m.pitch==pitch&&h.rejectedCommands()==1,"route overwrote guided owner");
        h.steerAngles(m,90,-60,45,20,HeadingController.Owner.SAFETY);
        require(h.owner()==HeadingController.Owner.SAFETY&&m.yaw!=yaw,"safety failed to preempt");
        h.beginTick(m);require(h.owner()==HeadingController.Owner.ROUTE,"ownership leaked into next tick");
        var motor=new dev.winefox.skyfarer.compat.tlm.RiptidePropulsion();
        var field=motor.getClass().getDeclaredField("plan");field.setAccessible(true);
        field.set(motor,new RiptidePlanner.Plan(0,-15,12,0,60,3,5,false,0,List.of()));
        var capability=new dev.winefox.skyfarer.compat.tlm.PropulsionCapability();
        var planning=new AsyncPlanning(m);planning.begin(0,"OWNERSHIP",null,0);
        var result=capability.decide(m,EquipmentLifecycleChecks.state(m),new PropulsionDemand(PropulsionDemand.Phase.STRIKE,0,12,60,.2),false,false,
            motor,new FireworkManager(),planning,h,(a,b,x,y)->true,d->{throw new AssertionError("active guided provider fell through to fireworks");});
        require(result.riptide()&&m.inventory.consumed==0,"provider ownership lost");planning.close();
        System.out.println("Control arbitration: guided owner, same-tick overwrite rejection, safety preemption, no dual ignition passed");
    }
    /** Hold the real executors with test-only latch jobs; no sleeps/hooks in production code. */
    static AutoCloseable blockWorkers()throws Exception {
        var release=new CountDownLatch(1);var started=new CountDownLatch(4);
        int total=0;
        for(String name:List.of("pool","geometryPool")){
            var field=PlannerExecutor.class.getDeclaredField(name);field.setAccessible(true);
            var executor=(ThreadPoolExecutor)field.get(null);
            int count=executor.getCorePoolSize();total+=count;
            for(int i=0;i<count;i++)executor.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        }
        for(int i=total;i<4;i++)started.countDown();
        if(!started.await(3,TimeUnit.SECONDS)){release.countDown();throw new AssertionError("workers never entered blackout");}
        return release::countDown;
    }
    static void replay(String name,int millis,boolean moving,boolean blackout)throws Exception {
        System.setProperty("wfs.replayTickMillis",Integer.toString(millis));SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var w=new ReplayWorld();w.loadedRadius=256;
        var m=new EntityMaid(w,new Vec3(0,9,-32));m.owner=new Player(w,Vec3.ZERO);
        m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.velocity=new Vec3(0,-.08,1.1);m.rain=true;
        m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var target=new LivingEntity(w,Vec3.ZERO);var controller=new AirCombatController(m);controller.acceptCommand(target);
        boolean obstacle=name.equals("obstacle-blackout"),preempted=false;
        int first=-1,hit=-1,exit=-1,heldAt=-1,continuous=0,maxContinuous=0;AutoCloseable blocked=null;
        var rows=new ArrayList<String>();boolean collision=false;
        try {
            for(int tick=1;tick<=450;tick++){
                if(moving){double q=(tick*.24+8)%32;target.pos=new Vec3(q<16?q-8:24-q,0,0);}
                w.advance(tick);controller.tick();var debug=controller.debugSnapshot();
                if(controller.spearAttackActive()){
                    if(first<0)first=tick;maxContinuous=Math.max(maxContinuous,++continuous);
                    if(blackout&&heldAt<0&&continuous>=3){
                        blocked=blockWorkers();heldAt=tick;
                        if(obstacle)w.boxes.add(new FlightCorridor.Box(-100,-5,m.getZ()+16,100,70,m.getZ()+17));
                    }
                }else continuous=0;
                if(blocked!=null&&tick-heldAt>=12){blocked.close();blocked=null;}
                if(obstacle&&heldAt>0&&ReplayWorld.events.stream().anyMatch(e->e.contains("EARLY_COLLISION_RECOVERY")))preempted=true;
                if(obstacle&&preempted&&tick>=heldAt+35){exit=tick;break;}
                require(!(controller.riptideActive()&&w.rockets.stream().anyMatch(r->r.alive)),"simultaneous guided and attached propulsion tick="+tick);
                if(hit<0&&controller.spearAttackActive()&&m.using&&m.usedTicks>=10){
                    var aim=new FlightPrediction.Aim(target.getX(),-3,target.getZ(),.3,7.8,0,0,0,0,2.25,.1,0);
                    if(m.velocity.length()>=.2&&aim.missSquared(EquipmentLifecycleChecks.state(m),0)<1e-8&&w.ray(m.getEyePosition(),target.getBoundingBox().getCenter())){
                        hit=tick;controller.recordSpearHit(target.id);target.alive=false;
                    }
                }
                rows.add(tick+"\t"+controller.state()+"\t"+m.pos+"\t"+m.yaw+"\t"+m.pitch+"\t"+debug.getString("control_owner")+"\t"+debug.getString("trajectory_status")+"\t"+debug.getString("reason"));
                if(hit>0&&tick>hit+25&&controller.state()!=AirCombatState.ATTACK_EXIT){exit=tick;break;}
                if(m.using)m.usedTicks++;
                var state=EquipmentLifecycleChecks.state(m);boolean powered=w.rockets.stream().anyMatch(r->r.alive&&r.createdAt<w.time);
                var next=FlightPrediction.step(state,m.yaw,m.pitch,180,90,90,.08,powered);
                if(controller.state()==AirCombatState.LANDING&&next.y()-m.getEyeHeight()<=0&&hit>0){exit=tick;break;}
                if(!new dev.winefox.skyfarer.safety.CorridorProbe(m).clear(state,next,0,0)){collision=true;break;}
                EquipmentLifecycleChecks.move(m,next);if(m.spinTicks>0)m.spinTicks--;
                for(var r:w.rockets)if(r.alive&&r.createdAt<w.time&&++r.life>r.lifetime)r.discard();
            }
        } finally {
            if(blocked!=null)blocked.close();
            var dir=Path.of("build/reports/trajectory-continuity");Files.createDirectories(dir);
            Files.write(dir.resolve(name+".tsv"),rows);Files.write(dir.resolve(name+".events.log"),ReplayWorld.events);
            controller.dispose("REPLAY_END",false);PlannerSession.stop();
        }
        String result=name+" first="+first+" hit="+hit+" exit="+exit+" continuous="+maxContinuous+" workerBlackout="+heldAt+" collision="+collision;
        System.out.println(result);require(first>0&&exit>0&&!collision&&(!blackout||heldAt>0)
            &&(obstacle?preempted:hit>0&&maxContinuous>=12),result);
    }
}
