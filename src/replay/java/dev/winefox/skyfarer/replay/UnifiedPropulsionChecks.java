package dev.winefox.skyfarer.replay;

import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.async.*;
import net.minecraft.world.item.*;
import java.util.List;

/** Same demand vocabulary for each phase; full native-motion/body/contact/exit rollouts. */
public final class UnifiedPropulsionChecks {
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static final GlidePlanner.Corridor OPEN=(a,b,h,v)->b.y()>.6&&Math.abs(b.x())<180&&Math.abs(b.z())<180;
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("stress")){
            System.setProperty("wfs.continuousMillis","5");continuousCombat();PlannerSession.stop();return;
        }
        System.setProperty("wfs.replayTickMillis","5");
        phases();continuations();blocked();emergencyExecution();repeated();continuousCombat();
        PlannerSession.stop();System.out.println("Unified propulsion phases, strike/exit/deadline rejection, emergency corridor, shortest duration, repeated READY and source fallback passed");
    }
    static void phases(){
        var start=new FlightPrediction.State(0,30,0,0,-.1,.7,0,0);
        for(var phase:List.of(PropulsionDemand.Phase.APPROACH,PropulsionDemand.Phase.REPOSITION,PropulsionDemand.Phase.AIR_RECOVERY,PropulsionDemand.Phase.EXIT,PropulsionDemand.Phase.TRANSIT,PropulsionDemand.Phase.EMERGENCY)){
            var d=new PropulsionDemand(phase,0,35,120,.9);
            var p=RiptidePlanner.choose(start,d,3,.08,OPEN,SurfaceLaunchPlanner.DRY);
            require(p!=null&&p.duration()<30,"no metered candidate for "+phase);
            require(RiptidePlanner.certify(start,p,RiptidePlanner.WINDUP,.08,OPEN,SurfaceLaunchPlanner.DRY)!=null,"candidate cannot recertify");
            for(int n=1;n<p.duration();n++){
                var shortPlan=new RiptidePlanner.Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),n,p.departure(),p.minimumSpeed(),0,List.of(),d);
                require(RiptidePlanner.certify(start,shortPlan,10,.08,OPEN,SurfaceLaunchPlanner.DRY)==null,"longer than necessary "+phase);
            }
            System.out.println("phase="+phase+" duration="+p.duration()+" fullTrace="+p.trace().size());
        }
        var jump=new FlightPrediction.State(0,.82,0,0,.3332,0,0,0);
        var departure=RiptidePlanner.choose(jump,new PropulsionDemand(PropulsionDemand.Phase.TAKEOFF,0,7,12,.9),3,.08,OPEN,SurfaceLaunchPlanner.DRY);
        require(departure!=null&&RiptidePlanner.departureReady(departure.trace().getLast(),departure),"departure readiness lost");
    }
    static void continuations(){
        var start=new FlightPrediction.State(0,12,0,0,-.1,.8,0,0);
        for(double movement:new double[]{0,.08,-.08}){
            var aim=new FlightPrediction.Aim(0,8,30,.3,5,movement,0,0,0,2.25,.1,10);
            var d=new PropulsionDemand(PropulsionDemand.Phase.REJOIN,0,11,30,.2,null,aim,8,false,100);
            var p=RiptidePlanner.choose(start,d,3,.08,OPEN,SurfaceLaunchPlanner.DRY);
            require(p!=null&&p.demand().path()!=null,"no funded intercept for motion="+movement);
            require(RiptidePlanner.certify(start,p.withDemand(p.demand().withDeadline(0)),10,.08,OPEN,SurfaceLaunchPlanner.DRY)==null,"ignored Spears charge deadline");
            var state=p.trace().get(Math.min(p.trace().size()-2,30));
            var exit=p.withDemand(new PropulsionDemand(PropulsionDemand.Phase.EXIT,0,12,60,.2,p.demand().path(),null,8,true));
            var chosen=RiptidePlanner.choose(state,exit.demand(),3,.08,OPEN,SurfaceLaunchPlanner.DRY);
            require(chosen!=null,"no certified exit propulsion");
            System.out.println("intercept motion="+movement+" duration="+p.duration()+" completeStrikeExitTrace="+p.trace().size()+" exitDuration="+chosen.duration());
        }
    }
    static void blocked(){
        var start=new FlightPrediction.State(0,30,0,0,-.1,.7,0,0);var d=new PropulsionDemand(PropulsionDemand.Phase.AIR_RECOVERY,0,35,120,.9);
        require(RiptidePlanner.choose(start,d,3,.08,(a,b,h,v)->false,SurfaceLaunchPlanner.DRY)==null,"blocked impulse allowed");
        require(RiptidePlanner.choose(start,d,0,.08,OPEN,SurfaceLaunchPlanner.DRY)==null,"unenchantable candidate generated");
        var wall=new FlightCorridor.Box(-30,0,9,30,80,10);
        GlidePlanner.Corridor space=(a,b,h,v)->OPEN.clear(a,b,h,v)&&!FlightCorridor.intersects(wall,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,h,v);
        var fast=new FlightPrediction.State(0,20,0,1,-.2,1.4,0,0);
        var emergency=RiptidePlanner.choose(fast,new PropulsionDemand(PropulsionDemand.Phase.EMERGENCY,0,23,64,.65),3,.08,space,SurfaceLaunchPlanner.DRY);
        require(emergency!=null,"no emergency Riptide turn before wall");
        require(emergency.trace().stream().allMatch(s->s.z()<9),"emergency crossed wall");
        System.out.println("emergency wall turn yaw="+emergency.yaw()+" duration="+emergency.duration());
    }
    static void emergencyExecution(){
        var w=new ReplayWorld();var m=RiptideReleaseChecks.maid(w);m.pos=new net.minecraft.world.phys.Vec3(0,11.6,0);m.velocity=new net.minecraft.world.phys.Vec3(0,-.1,.7);
        w.boxes.add(new FlightCorridor.Box(-30,-5,3,30,80,4));
        var motor=new RiptidePropulsion();var source=new PropulsionCapability();var planning=new AsyncPlanning(m);var heading=new HeadingController();var rockets=new FireworkManager();
        var demand=new PropulsionDemand(PropulsionDemand.Phase.EMERGENCY,0,15,64,.65);var space=new dev.winefox.skyfarer.safety.CorridorProbe(m);
        require(EmergencyAvoidancePlanner.choose(EquipmentLifecycleChecks.state(m),0,List.of(),.08,space)==null,"scenario no longer requires propulsion");
        // Warm finite snapshot/provider work before impact. Readiness itself never holds item use.
        int selected=-1;
        for(int tick=1;tick<100;tick++){
            w.advance(tick);motor.prepare(m,true);planning.begin(tick,"EMERGENCY_TEST",null,0);heading.beginTick(m);
            source.warm(m,EquipmentLifecycleChecks.state(m),demand,planning,space);
            if(!motor.ready(m))continue;
            var decision=source.decide(m,EquipmentLifecycleChecks.state(m),demand,false,false,motor,rockets,planning,heading,space,d->Double.POSITIVE_INFINITY);
            if(decision.riptide()){selected=tick;break;}
        }
        require(selected>0&&m.releaseCallbacks==1&&m.inventory.consumed==0,"emergency did not release preferred Riptide");
        int duration=motor.selectedDuration();
        for(int tick=selected+1;tick<selected+60&&motor.active();tick++){
            var start=EquipmentLifecycleChecks.state(m);var next=FlightPrediction.step(start,m.yaw,m.pitch,180,90,90,.08,false);
            require(space.clear(start,next,0,0),"emergency motor crossed wall");EquipmentLifecycleChecks.move(m,next);if(m.spinTicks>0)m.spinTicks--;
            w.advance(tick);motor.prepare(m,true);planning.begin(tick,"EMERGENCY_TEST",null,0);heading.beginTick(m);
            motor.tick(m,EquipmentLifecycleChecks.state(m),planning,heading);
        }
        require(motor.completed()&&m.glide&&m.pushCalls==duration,"emergency failed its certified recovery");
        System.out.println("actual emergency glide=unsolvable Riptide="+duration+" thrustTicks completed="+motor.completed()+" rockets="+m.inventory.consumed);
        planning.close();
    }
    static void continuousCombat()throws Exception{
        System.setProperty("wfs.replayTickMillis",System.getProperty("wfs.continuousMillis","20"));dev.winefox.skyfarer.config.SkyfarerConfig.DEBUG_LOGGING.set(true);ReplayWorld.events.clear();
        var w=new ReplayWorld();var m=RiptideReleaseChecks.maid(w);m.pos=new net.minecraft.world.phys.Vec3(0,9,-32);m.owner.pos=net.minecraft.world.phys.Vec3.ZERO;
        m.inventory.count=0;m.velocity=new net.minecraft.world.phys.Vec3(0,-.08,1.1);
        var first=new net.minecraft.world.entity.LivingEntity(w,net.minecraft.world.phys.Vec3.ZERO);
        var second=new net.minecraft.world.entity.LivingEntity(w,new net.minecraft.world.phys.Vec3(8,0,4));
        var controller=new dev.winefox.skyfarer.ai.AirCombatController(m);controller.acceptCommand(first);
        int hits=0,lastHit=0;boolean safeExit=false;
        for(int tick=1;tick<=500;tick++){
            if(first.alive){double q=(tick*.32+10)%40;first.pos=new net.minecraft.world.phys.Vec3(q<20?q-10:30-q,0,0);}
            w.advance(tick);controller.tick();
            if(controller.spearAttackActive()&&m.using&&m.usedTicks>=10)for(var target:List.of(first,second))if(target.alive){
                var aim=new FlightPrediction.Aim(target.getX(),-3,target.getZ(),.3,7.8,0,0,0,0,2.25,.1,0);
                if(m.velocity.length()>=.2&&aim.missSquared(EquipmentLifecycleChecks.state(m),0)<1e-8&&w.ray(m.getEyePosition(),target.getBoundingBox().getCenter())){
                    controller.recordSpearHit(target.id);target.alive=false;hits++;lastHit=tick;
                }
            }
            if(hits==2&&tick>lastHit+25&&controller.state()!=dev.winefox.skyfarer.ai.AirCombatState.ATTACK_EXIT){safeExit=true;break;}
            if(m.using)m.usedTicks++;
            var start=EquipmentLifecycleChecks.state(m);var next=FlightPrediction.step(start,m.yaw,m.pitch,180,90,90,.08,false);
            if(hits==2&&controller.state()==dev.winefox.skyfarer.ai.AirCombatState.LANDING&&next.y()-m.getEyeHeight()<=0){safeExit=true;break;}
            require(new dev.winefox.skyfarer.safety.CorridorProbe(m).clear(start,next,0,0),"continuous combat body collision tick="+tick+" hits="+hits+" state="+controller.state()+" pos="+m.pos+" events="+ReplayWorld.events.stream().skip(Math.max(0,ReplayWorld.events.size()-16)).toList());
            EquipmentLifecycleChecks.move(m,next);if(m.spinTicks>0)m.spinTicks--;
            require(!m.ground&&m.getY()>0,"continuous combat landed between targets");
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/trajectory-continuity"));
        java.nio.file.Files.write(java.nio.file.Path.of("build/reports/trajectory-continuity/historic-stress.events.log"),ReplayWorld.events);
        require(hits==2&&safeExit&&m.inventory.consumed==0&&m.releaseCallbacks>0,"continuous combat incomplete hits="+hits+" exit="+safeExit+" releases="+m.releaseCallbacks+" pos="+m.pos);
        System.out.println("continuous combat targets=2 hits="+hits+" safeExit="+safeExit+" releases="+m.releaseCallbacks+" rockets="+m.inventory.consumed);controller.dispose("TEST",false);
    }
    static void repeated(){
        var w=new ReplayWorld();var m=RiptideReleaseChecks.maid(w);var motor=new RiptidePropulsion();var source=new PropulsionCapability();var planning=new AsyncPlanning(m);var heading=new HeadingController();
        int bursts=0;boolean wasActive=false;
        for(int tick=1;tick<160&&bursts<2;tick++){
            w.advance(tick);motor.prepare(m,true);planning.begin(tick,"REPEATED",null,0);heading.beginTick(m);
            if(motor.active())motor.tick(m,EquipmentLifecycleChecks.state(m),planning,heading);
            else {
                int choice=source.prefer(m,EquipmentLifecycleChecks.state(m),new PropulsionDemand(PropulsionDemand.Phase.AIR_RECOVERY,m.getX(),m.getEyeY()+4,m.getZ()+90,.9),false,motor,planning,heading,new dev.winefox.skyfarer.safety.CorridorProbe(m));
                if(choice==2)bursts++;
            }
            if(motor.active()||wasActive){EquipmentLifecycleChecks.move(m,FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,false));if(m.spinTicks>0)m.spinTicks--;}
            wasActive=motor.active();
        }
        require(bursts==2&&m.releaseCallbacks==2&&m.offhand.damage==2&&m.inventory.consumed==0,"continuous air propulsion lost second burst");
        motor.cancel(m);m.rain=false;require(PropulsionCapability.source(m).equals("FIREWORK"),"dry source fallback failed");
        m.inventory.count=0;require(PropulsionCapability.rejection(m)!=null,"dry empty inventory accepted");planning.close();
    }
}
