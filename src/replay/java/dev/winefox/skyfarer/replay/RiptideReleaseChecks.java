package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.RiptidePropulsion;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import dev.winefox.skyfarer.config.SkyfarerConfig;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** External item-use event/JumpControl fixture; controller, planner and release adapter are production. */
public final class RiptideReleaseChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","5");SkyfarerConfig.DEBUG_LOGGING.set(true);
        run("SUCCESS");run("RAIN_LOST");run("WRONG_HAND");run("RELEASE_VETO");run("START_VETO");
        PlannerSession.stop();System.out.println("Riptide release checks passed: explicit offhand, delayed real charge, takeoff persistence, release callback/impulse exactly once, start/stop event veto, weather/hand cancellation, spear restoration.");
    }
    static void run(String mode)throws Exception {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,Vec3.ZERO);
        m.owner=new Player(w,new Vec3(8,0,8));m.rain=true;m.inventory.count=0;
        var spear=m.weapon;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var trident=m.offhand;new LivingEntity(w,new Vec3(0,6,32));var c=new AirCombatController(m);
        var manager=(RiptidePropulsion)RiptidePriorityChecks.field(c,"riptide");
        m.vetoUseStart=mode.equals("START_VETO");m.vetoRelease=mode.equals("RELEASE_VETO");
        // Calling the adapter outside the certified release scope must do nothing.
        RiptidePropulsion.releaseAuthorized(m,trident,71980);
        require(m.spinTicks==0&&trident.damage==0,"unowned release accepted");
        int began=-1;boolean mutated=false;
        for(int tick=1;tick<130;tick++) {
            w.advance(tick);int chargeBefore=m.using?m.usedTicks:0;c.tick();
            if(m.using&&began<0)began=tick;
            if(began>=0&&!mutated&&m.usedTicks>=4&&!mode.equals("SUCCESS")&&!mode.equals("RELEASE_VETO")) {
                mutated=true;if(mode.equals("RAIN_LOST"))m.rain=false;
                if(mode.equals("WRONG_HAND")){m.stopUsingItem();m.startUsingItem(InteractionHand.MAIN_HAND);}
            }
            if(!mutated&&m.using) {
                require(m.getUsedItemHand()==InteractionHand.OFF_HAND&&m.getUseItem()==trident,"main spear stole offhand use");
                require(m.weapon==spear&&m.mainStarts==0,"spear charge started while trident charging");
                SpearCompat.startCharge(m);SpearCompat.stopCharge(m);
                require(m.using&&m.getUseItem()==trident,"spear logic cancelled offhand use");
            }
            if(began>=0&&tick-began<4)require(m.ground&&m.releaseCalls==0,"wall clock bypassed actual charge");
            if(m.releaseCalls>0){require(chargeBefore>=10,"released before actual charge");break;}
            if(mutated&&!manager.active())break;
            // Simulate a four-tick item-use delay independently of game time.
            if(m.using&&tick-began>=4)m.usedTicks++;
            if(!m.ground&&!m.glide){require(chargeBefore>=10,"jump interrupted incomplete charge");m.pos=m.pos.add(m.velocity);m.velocity=new Vec3(m.velocity.x*.91,(m.velocity.y-.08)*.98,m.velocity.z*.91);}
        }
        if(mode.equals("SUCCESS")) {
            require(m.releaseCalls==1&&m.releaseCallbacks==1&&m.spinTicks>0&&trident.damage==1&&!m.using&&m.pushCalls==1,"native release did not apply impulse/spin exactly once");
            var velocity=m.velocity;RiptidePropulsion.releaseAuthorized(m,trident,71980);
            require(m.velocity==velocity&&trident.damage==1,"duplicate unowned release fired");
            // Complete the actual thrust AND certified recovery, without retrying release.
            var planning=(AsyncPlanning)RiptidePriorityChecks.field(c,"planning");var heading=(HeadingController)RiptidePriorityChecks.field(c,"heading");
            for(int i=0;manager.active()&&i<80;i++) {
                EquipmentLifecycleChecks.move(m,FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,false));
                if(m.spinTicks>0)m.spinTicks--;
                w.advance(w.getGameTime()+1);planning.begin(w.getGameTime(),"RELEASE_RECOVERY",null,0);heading.beginTick(m);
                manager.tick(m,EquipmentLifecycleChecks.state(m),planning,heading);
            }
            require(manager.completed(),"normal recovery completion not recognized");
            require(m.releaseCalls==1&&!m.using,"spin completion restarted release/use");
            SpearCompat.startCharge(m);require(m.using&&m.getUsedItemHand()==InteractionHand.MAIN_HAND&&m.getUseItem()==spear&&m.usedTicks==0,"spear state not restored");
            SpearCompat.stopCharge(m);require(!m.using,"spear stop failed");
        } else {
            require(m.spinTicks==0&&trident.damage==0,"cancel/veto applied impulse or wear: "+mode);
            if(mode.equals("RELEASE_VETO"))require(m.releaseCalls==1&&m.releaseCallbacks==0&&!manager.active(),"release veto ignored");
            if(mode.equals("RAIN_LOST"))require(!manager.active()&&!m.using&&m.releaseCalls==0,"weather loss failed to cancel");
            if(mode.equals("WRONG_HAND"))require(!manager.active()&&m.getUsedItemHand()==InteractionHand.MAIN_HAND,"hand takeover not detected");
            if(mode.equals("START_VETO"))require(m.offhandStarts==0&&m.releaseCalls==0&&!manager.active(),"start veto ignored");
        }
        require(ReplayWorld.events.stream().anyMatch(s->s.contains(m.id.toString())&&s.contains("sameUse=")),"use diagnostics missing");
        c.dispose("TEST_END",false);System.out.println("release scenario="+mode+" starts="+m.offhandStarts+" releases="+m.releaseCalls+" callbacks="+m.releaseCallbacks);
    }
}
