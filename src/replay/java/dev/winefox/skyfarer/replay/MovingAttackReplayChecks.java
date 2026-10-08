package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Real controller/mailbox/physics; the external minecart is a bounded position track.
 * Contact is the native Spears segment proxy, not an in-game damage integration test. */
public final class MovingAttackReplayChecks {
    public static void main(String[] args)throws Exception{
        System.setProperty("wfs.replayTickMillis","20");
        boolean baseline=args.length>0&&args[0].equals("baseline");
        var results=new ArrayList<String>();results.add("scenario,firstAttack,hit,safeExit,maxDistance,collision");
        boolean passed=true;
        for(String name:List.of("static","small","minecart","lateral","reverse"))passed&=run(name,baseline,results);
        var dir=Path.of("build/reports/moving-attack");Files.createDirectories(dir);
        Files.write(dir.resolve(baseline?"baseline.csv":"current.csv"),results);
        PlannerSession.stop();if(!baseline&&!passed)throw new AssertionError("Moving attack replay failed: "+results);
    }
    private static boolean run(String name,boolean baseline,List<String> results)throws Exception{
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        var w=new ReplayWorld();w.loadedRadius=256;
        var m=new EntityMaid(w,new Vec3(0,9,-32));m.owner=new Player(w,Vec3.ZERO);
        m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.velocity=new Vec3(0,-.08,1.1);
        m.rain=true;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var target=new LivingEntity(w,Vec3.ZERO);var c=new AirCombatController(m);c.acceptCommand(target);
        int first=-1,hit=-1,exit=-1;double max=0;boolean collision=false;var trace=new ArrayList<String>();
        for(int tick=1;tick<=600;tick++){
            double x=switch(name){case "small"->2*Math.sin(tick*.06);case "minecart"->triangle(tick,.32,10);
                case "lateral"->Math.min(22,tick*.24);case "reverse"->triangle(tick+25,.4,12);default->0;};
            // Passenger velocity can be zero even while its world position follows the vehicle.
            target.pos=new Vec3(x,0,0);w.advance(tick);c.tick();
            var s=EquipmentLifecycleChecks.state(m);var debug=c.debugSnapshot();
            max=Math.max(max,m.position().subtract(target.position()).length());
            if(c.spearAttackActive()&&first<0)first=tick;
            if(hit<0&&c.spearAttackActive()&&m.using&&m.usedTicks>=10){
                var aim=new FlightPrediction.Aim(x,-3,0,.3,7.8,0,0,0,0,2.25,.1,0);
                if(m.velocity.length()>=.2&&aim.missSquared(s,0)<1e-8&&w.ray(m.getEyePosition(),target.getBoundingBox().getCenter())){
                    hit=tick;c.recordSpearHit(target.id);target.alive=false;
                }
            }
            if(hit>0&&tick>hit+25&&c.state()!=AirCombatState.ATTACK_EXIT){exit=tick;break;}
            if(m.using)m.usedTicks++;
            boolean powered=w.rockets.stream().anyMatch(r->r.alive&&r.createdAt<w.time);
            var next=FlightPrediction.step(s,m.yaw,m.pitch,180,90,90,.08,powered);
            if(c.state()==AirCombatState.LANDING&&next.y()-m.getEyeHeight()<=0&&hit>0){exit=tick;break;}
            if(m.glide&&!new CorridorProbe(m).clear(m.getEyePosition(),new Vec3(next.x(),next.y(),next.z()),0,0)){
                collision=true;break;
            }
            EquipmentLifecycleChecks.move(m,next);if(m.spinTicks>0)m.spinTicks--;
            for(var r:w.rockets)if(r.alive&&r.createdAt<w.time&&++r.life>r.lifetime)r.discard();
            trace.add(tick+"\t"+c.state()+"\t"+m.pos+"\t"+target.pos+"\t"+debug.getString("reason"));
            if(m.getY()<=0)break;
        }
        String result=name+","+first+","+hit+","+exit+","+String.format(Locale.ROOT,"%.2f",max)+","+collision;
        results.add(result);System.out.println(result);
        var dir=Path.of("build/reports/moving-attack");Files.createDirectories(dir);
        String prefix=(baseline?"baseline-":"current-")+name;
        Files.write(dir.resolve(prefix+".tsv"),trace);Files.write(dir.resolve(prefix+".events.log"),ReplayWorld.events);
        c.dispose("REPLAY_END",false);
        return first>0&&hit>0&&exit>0&&!collision&&max<80;
    }
    private static double triangle(int tick,double speed,double radius){double q=(tick*speed+radius)%(4*radius);return q<2*radius?q-radius:3*radius-q;}
}
