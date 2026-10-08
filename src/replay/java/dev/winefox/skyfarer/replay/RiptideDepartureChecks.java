package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.RiptidePropulsion;
import dev.winefox.skyfarer.config.SkyfarerConfig;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Full controller departure through charge, jump, release, every thrust tick and post-burst flight. */
public final class RiptideDepartureChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why+" events="+ReplayWorld.events.stream().skip(Math.max(0,ReplayWorld.events.size()-12)).toList());}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","5");SkyfarerConfig.DEBUG_LOGGING.set(true);
        run(10,true);run(6,true);run(32,true);run(60,true);run(32,false);rejectedLaunchFallback();continuous();
        PlannerSession.stop();System.out.println("Actual controller near/far Riptide departures, complete metered thrust, post-burst flight and dry firework fallback passed");
    }
    static void continuous() {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,new Vec3(0,80,0));
        m.owner=new Player(w,new Vec3(20,80,30));m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.rain=true;
        m.velocity=new Vec3(0,-.1,.7);m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var planning=new AsyncPlanning(m);var manager=new RiptidePropulsion();var heading=new HeadingController();
        long tick=1;RiptidePlanner.Plan selected=null;
        for(;tick<180;tick++) {
            w.advance(tick);planning.begin(tick,"LONG_BURST",null,0);heading.beginTick(m);manager.prepare(m,true);
            selected=planning.propulsion(EquipmentLifecycleChecks.state(m),new PropulsionDemand(PropulsionDemand.Phase.APPROACH,0,160,120,.55),3,false,new dev.winefox.skyfarer.safety.CorridorProbe(m));
            if(selected!=null&&manager.begin(m,selected,EquipmentLifecycleChecks.state(m),planning,heading))break;
        }
        require(manager.active()&&selected.duration()>10&&selected.duration()<30,"long request did not select a bounded variable duration");
        int duration=selected.duration();long started=tick;
        while(manager.active()&&tick<started+60) {
            EquipmentLifecycleChecks.move(m,FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,false));
            if(m.using)m.usedTicks++;if(m.spinTicks>0)m.spinTicks--;
            w.advance(++tick);planning.begin(tick,"LONG_BURST",null,0);heading.beginTick(m);
            var before=m.velocity;manager.tick(m,EquipmentLifecycleChecks.state(m),planning,heading);
            if(!manager.active())require(manager.completed()&&m.velocity==before&&m.glide,"long burst interrupted or cutoff erased momentum");
        }
        require(manager.completed()&&m.pushCalls==duration&&m.releaseCalls==1&&m.spinTicks==0,"long thrust count/lifecycle mismatch");
        System.out.println("continuous duration="+duration+" chargeAndBurstTicks="+(tick-started)+" pushes="+m.pushCalls+" cutoffVelocity="+m.velocity);
        planning.close();
    }
    static void run(double distance,boolean wet)throws Exception {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,new Vec3(8,0,8));
        m.rain=wet;m.inventory.count=wet?0:64;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var target=new LivingEntity(w,new Vec3(0,0,distance));var c=new AirCombatController(m);
        if(distance>32){CommandAccess.targets=x->List.of(target);CommandAccess.designated=(x,t)->t==target;CommandAccess.anchor=x->target.position();c.acceptCommand(target);}
        var manager=(RiptidePropulsion)RiptidePriorityChecks.field(c,"riptide");
        int duration=0,releaseTick=-1,completedTick=-1;
        for(int tick=1;tick<180;tick++) {
            w.advance(tick);var before=m.velocity;int pushes=m.pushCalls;c.tick();
            if(m.pushCalls>pushes)require(Math.abs(m.velocity.subtract(before).length()-RiptidePlanner.THRUST)<1e-6,"execution thrust differs from predicted per-tick impulse");
            if(manager.active()&&completedTick<0)duration=manager.selectedDuration();
            if(m.releaseCalls>0&&releaseTick<0)releaseTick=tick;
            if(manager.completed()&&completedTick<0){
                completedTick=tick;
                require(m.velocity.horizontalDistance()>=.9&&m.getY()>=5,"handoff has insufficient speed/height");
                require(tick>=releaseTick+duration+RiptidePlanner.DEPARTURE_RECOVERY,"recovery guidance skipped");
            }
            if(wet&&completedTick<0)require(!c.spearAttackActive(),"uncertified Strike during departure");
            if(releaseTick>=0&&tick<=releaseTick+duration)require(m.pushCalls==Math.min(tick-releaseTick+1,duration),"actual impulse count diverged from duration");
            if(wet&&completedTick>=0) {
                if(tick==completedTick)require(m.spinTicks==0&&m.pushCalls==duration&&m.offhand.damage==1,"cutoff/spin/durability mismatch");
                require(m.glide&&!m.ground,"ordinary departure collapsed to ground");
                if(tick>=completedTick+40){require(m.getY()>3,"departure had no usable height after normal controller turn");break;}
            }
            if(!wet&&m.inventory.consumed>0)break;
            if(m.using)m.usedTicks++;
            if(m.glide) {
                var s=EquipmentLifecycleChecks.state(m);
                boolean rocket=w.rockets.stream().anyMatch(r->r.alive&&r.createdAt<w.time);
                var next=FlightPrediction.step(s,m.yaw,m.pitch,180,90,90,.08,rocket);
                require(new dev.winefox.skyfarer.safety.CorridorProbe(m).clear(s,next,0,0),"departure collided with terrain");
                EquipmentLifecycleChecks.move(m,next);
            } else if(!m.ground){m.pos=m.pos.add(m.velocity);m.velocity=new Vec3(m.velocity.x*.91,(m.velocity.y-.08)*.98,m.velocity.z*.91);}
            if(m.getY()<=0){m.pos=new Vec3(m.getX(),0,m.getZ());m.ground=true;m.glide=false;m.pose=Pose.STANDING;m.velocity=Vec3.ZERO;}
            if(m.spinTicks>0)m.spinTicks--;
        }
        if(wet){
            require(releaseTick>0&&completedTick>=releaseTick+duration&&duration<30,"near/far ordinary Riptide departure failed distance="+distance);
            require(m.inventory.consumed==0&&m.releaseCalls>=1,"departure burned a rocket or never released");
            require(ReplayWorld.events.stream().anyMatch(s->s.contains(m.id.toString())&&s.contains("RIPTIDE_LAUNCH_COMPLETE")),"no completed departure transition");
        }else require(m.releaseCalls==0&&m.inventory.consumed>0,"dry trident prevented rocket fallback");
        System.out.println("departure targetDistance="+distance+" wet="+wet+" duration="+duration+" thrustCalls="+m.pushCalls+" altitude="+m.getY()+" state="+c.state()+" rockets="+m.inventory.consumed);
        c.dispose("TEST_END",false);
        CommandAccess.targets=x->List.of();CommandAccess.designated=(x,t)->false;CommandAccess.anchor=x->null;
    }
    static void rejectedLaunchFallback() {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,new Vec3(8,0,8));
        m.rain=true;m.vetoRelease=true;m.inventory.count=64;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        new LivingEntity(w,new Vec3(0,0,32));var c=new AirCombatController(m);
        for(int tick=1;tick<180&&m.inventory.consumed==0;tick++){
            w.advance(tick);c.tick();
            if(m.glide)EquipmentLifecycleChecks.move(m,FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,false));
            else if(!m.ground){m.pos=m.pos.add(m.velocity);m.velocity=new Vec3(m.velocity.x*.91,(m.velocity.y-.08)*.98,m.velocity.z*.91);}
            if(m.getY()<=0){m.ground=true;m.glide=false;m.pose=Pose.STANDING;m.pos=new Vec3(m.getX(),0,m.getZ());m.velocity=Vec3.ZERO;}
        }
        require(m.inventory.consumed>0&&m.releaseCallbacks==0,"rejected Riptide keeps restarting and starves rocket launch");
        c.dispose("TEST_END",false);System.out.println("exhausted Riptide execution -> ordinary firework departure passed");
    }
}
