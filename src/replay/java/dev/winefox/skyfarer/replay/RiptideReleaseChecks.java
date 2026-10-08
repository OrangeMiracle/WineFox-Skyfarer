package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Production precharge/provider/release against external item/event fixtures. No fake entity use. */
public final class RiptideReleaseChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","5");
        resources();release(false);release(true);
        PlannerSession.stop();System.out.println("Independent readiness/release passed: 10 ticks, held ready, main Spears uninterrupted, exact thrust/wear, event veto, environment/equipment invalidation and recharge.");
    }
    static EntityMaid maid(ReplayWorld w){
        w.loadedRadius=256;var m=new EntityMaid(w,new Vec3(0,80,0));m.owner=new Player(w,new Vec3(20,80,30));
        m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.rain=true;m.velocity=new Vec3(0,-.1,.7);
        m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;return m;
    }
    static void resources(){
        var w=new ReplayWorld();var m=maid(w);var motor=new RiptidePropulsion();
        m.startUsingItem(InteractionHand.MAIN_HAND);var spear=m.getUseItem();
        for(int i=1;i<=30;i++){w.advance(i);motor.prepare(m,true);m.usedTicks++;
            require(m.using&&m.getUseItem()==spear&&m.offhandStarts==0,"precharge stole Spears use");
            require(motor.ready(m)==(i>=10),"10 tick readiness/held ready mismatch "+i);
        }
        m.rain=false;require(!motor.ready(m),"invalid environment retained readiness");
        motor.prepare(m,true);m.rain=true;require(!motor.ready(m),"weather toggle reused readiness");
        for(int i=31;i<=40;i++){w.advance(i);motor.prepare(m,true);}
        require(motor.ready(m),"weather recharge failed");
        var old=m.offhand;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        require(!motor.ready(m),"replacement trident inherited ready");motor.prepare(m,true);m.offhand=old;
        for(int i=41;i<=50;i++){w.advance(i);motor.prepare(m,true);}
        require(motor.ready(m),"replacement recharge failed");
        m.chest=new ItemStack(Items.ELYTRA);require(!motor.ready(m),"equipment change retained ready");
        w.advance(51);motor.prepare(m,false);require(!motor.ready(m),"ineligible controller retained charge");
        require(m.using&&m.getUseItem()==spear&&m.usedTicks==30,"invalidation changed Spears lifecycle");
    }
    static void release(boolean veto){
        var w=new ReplayWorld();var m=maid(w);var motor=new RiptidePropulsion();var choice=new PropulsionCapability();
        var planning=new AsyncPlanning(m);var heading=new HeadingController();var trident=m.offhand;
        m.startUsingItem(InteractionHand.MAIN_HAND);m.usedTicks=15;var spear=m.getUseItem();m.vetoRelease=veto;
        RiptidePropulsion.releaseAuthorized(m,trident,71980);require(m.pushCalls==0&&trident.damage==0,"unowned release");
        int outcome=0;long tick=1;
        for(;tick<100&&m.releaseCalls==0;tick++){
            w.advance(tick);motor.prepare(m,true);planning.begin(tick,"READY_TEST",null,0);heading.beginTick(m);
            outcome=choice.prefer(m,EquipmentLifecycleChecks.state(m),new PropulsionDemand(PropulsionDemand.Phase.APPROACH,0,85,120,.55),false,motor,planning,heading,new dev.winefox.skyfarer.safety.CorridorProbe(m));
        }
        require(m.releaseCalls==1,"no local item release");
        require(m.using&&m.getUseItem()==spear&&m.getUsedItemHand()==InteractionHand.MAIN_HAND&&m.usedTicks==15&&m.offhandStarts==0,"release polluted main-hand charge");
        if(veto){require(!motor.active()&&m.pushCalls==0&&trident.damage==0,"event veto ignored");}
        else{
            require(outcome==2&&m.pushCalls==1&&trident.damage==1&&m.spinTicks>0,"READY did not release immediately");
            require(!RiptideReadiness.of(m).ready(m,3),"spent resource retained");int duration=motor.selectedDuration();
            while(motor.active()&&tick<180){
                EquipmentLifecycleChecks.move(m,FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,false));
                if(m.spinTicks>0)m.spinTicks--;
                w.advance(tick++);motor.prepare(m,true);planning.begin(w.time,"READY_TEST",null,0);heading.beginTick(m);
                motor.tick(m,EquipmentLifecycleChecks.state(m),planning,heading);
            }
            require(motor.completed()&&m.pushCalls==duration&&m.spinTicks==0&&m.glide,"predicted duration/spin cutoff failed");
            for(int i=0;i<10;i++){w.advance(tick++);motor.prepare(m,true);}
            require(motor.ready(m)&&m.releaseCalls==1&&trident.damage==1,"10-tick refill or exactly-once failed");
        }
        require(m.using&&m.getUseItem()==spear,"motor completion stopped Spears");planning.close();
    }
}
