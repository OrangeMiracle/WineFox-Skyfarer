package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class EquipmentFixChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        System.setProperty("wfs.replayTickMillis","1");
        durability();qualification();flight();ground();navigationJump();notices();PlannerSession.stop();
        System.out.println("Equipment fixes passed: Unbreakable/tiny/zero durability + vanilla 19/20, airborne return, real ground behavior approach/contact/dead-zone, persistent notices and 20-maid throttling.");
    }
    static void qualification() {
        var mod=new ItemStack(Items.DIAMOND_CHESTPLATE);mod.maxDamage=1;mod.damage=1;mod.unbreakable=true;
        var ids=List.of("minecraft:diamond_chestplate");
        require(WingInventory.qualification(mod,List.of(),s->true)==WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT,"empty active list misreported as durability");
        require(WingInventory.qualification(mod,ids,s->true)==WingInventory.Qualification.READY,"listed Unbreakable rejected");
        require(WingInventory.qualification(mod,ids,s->false)==WingInventory.Qualification.ITEM_CANNOT_GLIDE,"capability failure misreported");
        mod.unbreakable=false;require(WingInventory.qualification(mod,ids,s->true)==WingInventory.Qualification.LOW_DURABILITY,"breakable low wing admitted");
        var inv=new net.neoforged.neoforge.items.ItemStackHandler(2);inv.setStackInSlot(0,mod);
        var spare=new ItemStack(Items.ELYTRA);inv.setStackInSlot(1,spare);var trace=new ArrayList<String>();
        require(WingInventory.exchange(inv,mod,ids,s->true,(slot,reason)->trace.add(slot+":"+reason))==spare,"replacement did not recover qualification");
        require(trace.equals(List.of("0:LOW_DURABILITY","1:ACCEPTED")),"replacement diagnostic chain incomplete");
        var w=new ReplayWorld();var m=new EntityMaid(w,Vec3.ZERO);
        dev.winefox.skyfarer.config.SkyfarerConfig.DEBUG_LOGGING.set(true);
        EquipmentDiagnostics.clear();
        require(EquipmentDiagnostics.begin(m,mod,WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT),"first failure absent from debug");
        require(!EquipmentDiagnostics.begin(m,mod,WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT),"debug repeated same tick");
        w.time=201;require(EquipmentDiagnostics.begin(m,mod,WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT),"persistent episode hid future diagnostics");
        EquipmentDiagnostics.result(m,WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT,"NO_USABLE_SPARE",WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT);
        require(ReplayWorld.events.stream().anyMatch(s->s.contains("equipmentQualified=false reason=NOT_ELYTRA_EQUIVALENT")),"missing final exact rejection");
        dev.winefox.skyfarer.config.SkyfarerConfig.DEBUG_LOGGING.set(false);
    }
    static void navigationJump() {
        var w=new ReplayWorld();var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,new Vec3(0,0,-3));m.wingDurability=19;
        var enemy=new LivingEntity(w,new Vec3(0,1,8));var c=new AirCombatController(m);
        c.tick();var walk=m.brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        for(int i=0;i<5;i++){w.time++;c.tick();require(m.brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()==walk,"pursuit replaced same live tracker");}
        // External navigation supplies a normal jump over a one-block lip; actual controller must not
        // clear the path or seize the movement while onGround is false (the 0.6.2.3 regression).
        m.ground=false;m.velocity=new Vec3(0,.42,.18);boolean crossed=false,landed=false;
        for(int i=0;i<16&&!landed;i++) {
            w.time++;var velocity=m.velocity;c.tick();
            require(!c.controlsAir()&&c.state()==AirCombatState.GROUNDED&&!m.glide,"ordinary step jump became flight recovery");
            require(m.velocity==velocity&&m.navigation.stops==0,"ground controller overwrote navigation momentum/path");
            require(m.brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()==walk,"jump lost current path target");
            var next=m.pos.add(m.velocity);
            if(crossed&&next.y<=1&&m.velocity.y<0){next=new Vec3(next.x,1,next.z);landed=true;m.ground=true;}
            if(next.z>=1&&next.z<2){require(next.y>=1,"jump failed to clear one-block lip");crossed=true;}
            m.pos=next;m.velocity=new Vec3(0,(m.velocity.y-.08)*.98,.18);
        }
        require(crossed&&landed&&m.pos.y==1,"one-block step replay did not land");
        for(int i=0;i<40&&m.meleeHits==0;i++){w.time++;c.tick();if(m.meleeHits==0)m.pos=m.pos.add(0,0,.1);}
        require(m.meleeHits==1&&!c.controlsAir(),"step navigation never reached ordinary melee contact");
        require(!m.reports.contains("return"),"ordinary jump announced emergency return");c.dispose("END",false);
    }
    static void durability() {
        WingInventoryReplayChecks.run();
        var ids=List.of("minecraft:diamond_chestplate","cataclysm:ignitium_elytra_chestplate");
        var mod=new ItemStack(Items.DIAMOND_CHESTPLATE);mod.maxDamage=1;mod.damage=1;mod.unbreakable=true;
        require(mod.isDamageableItem(),"fixture must exercise conflicting item damageability");
        require(WingInventory.remaining(mod)==Integer.MAX_VALUE&&WingInventory.ready(mod,ids,s->true),"Unbreakable internal damage rejected flight");
        require(!WingInventory.ready(mod,ids,s->false),"Unbreakable granted flight capability");
        mod.unbreakable=false;require(!WingInventory.ready(mod,ids,s->true),"breakable tiny item bypassed safety");
        mod.maxDamage=0;require(WingInventory.ready(mod,ids,s->true),"no consumable durability rejected flight");
    }
    static void flight() {
        var w=new ReplayWorld();var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,Vec3.ZERO);m.wingDurability=19;
        var c=new AirCombatController(m);w.advance(1);c.tick();require(!m.glide&&c.state()==AirCombatState.GROUNDED,"19 took off");
        m.pos=new Vec3(0,80,0);m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.velocity=new Vec3(0,-.2,.8);
        c.acceptCommand(new LivingEntity(w,new Vec3(0,75,30)));w.advance(2);c.tick();
        require(m.glide&&c.controlsAir()&&(c.state()==AirCombatState.RETURN_TO_OWNER||c.state()==AirCombatState.LANDING),"19 revoked glide/continued attack: "+c.state());c.dispose("END",true);
    }
    static void ground() {
        var w=new ReplayWorld();var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,new Vec3(0,0,-5));var enemy=new LivingEntity(w,new Vec3(0,0,6));
        GroundSpearCombat.tick(m);require(m.meleeHits==0&&m.swings==0&&m.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),"out-of-range stopped/swung");
        for(int step=1;step<=45&&m.meleeHits==0;step++) {
            require(m.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),"stopped before melee contact");
            m.pos=m.pos.add(0,0,.1);GroundSpearCombat.tick(m);
        }
        require(m.meleeHits==1&&m.swings==1&&!m.using&&!m.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),"did not reach, stop and left-click hit");
        m.brain.eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);m.pos=new Vec3(0,0,5.5);GroundSpearCombat.tick(m);
        var walk=m.brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        require(m.meleeHits==1&&walk.target() instanceof Vec3 away&&away.z<4,"ordinary spear inner dead-zone caused empty swinging");
        // A valid stab must not cancel retreat from a mob advancing into our spacing band.
        m.pos=new Vec3(0,0,3.4);GroundSpearCombat.tick(m);
        require(m.meleeHits==2&&m.brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().target() instanceof Vec3 retreat&&retreat.z<m.pos.z,"attack canceled safe spacing retreat");
        m.brain.eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);m.pos=Vec3.ZERO;
        GroundSpearCombat.tick(m);require(m.meleeHits==2,"base left-click reached distant target");
        m.favorabilityBonus=2;GroundSpearCombat.tick(m);
        require(m.meleeHits==3,"favorability only changed stop distance, not contact");
        m.brain.eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);m.favorabilityBonus=0;m.interactionRange=4;
        GroundSpearCombat.tick(m);require(m.meleeHits==4,"interaction attribute bonus omitted");
        m.brain.eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);m.pos=new Vec3(0,0,5.5);
        GroundSpearCombat.tick(m);require(m.meleeHits==4,"range bonus removed inner blind zone");
    }
    static void notices() {
        EquipmentNotices.clear();var w=new ReplayWorld();var owner=new Player(w,Vec3.ZERO);var maid=new EntityMaid(w,new Vec3(0,80,0));maid.owner=owner;maid.ground=false;
        for(int t=0;t<600;t++){w.time=t;EquipmentNotices.tick(maid,false);}
        require(maid.reports.equals(List.of("return")),"continuous fault repeated return");
        var reload=new EntityMaid(w,maid.pos);reload.id=maid.id;reload.owner=owner;reload.ground=false;reload.persistent=maid.persistent;
        for(int t=600;t<900;t++){w.time=t;EquipmentNotices.tick(reload,false);}
        require(reload.reports.isEmpty(),"replacement/reload forgot episode");
        for(int t=900;t<1200;t++){w.time=t;reload.ground=t%2==0;EquipmentNotices.tick(reload,false);}
        require(reload.reports.size()<=1,"landing bounce repeated speech");
        for(int t=1200;t<1250;t++){w.time=t;EquipmentNotices.tick(reload,true);}
        reload.ground=false;for(int t=1250;t<1550;t++){w.time=t;EquipmentNotices.tick(reload,false);}
        require(Collections.frequency(reload.reports,"return")==1,"genuine repair did not rearm future fault");
        EquipmentNotices.clear();var many=new ArrayList<EntityMaid>();
        for(int i=0;i<20;i++){var m=new EntityMaid(w,new Vec3(i,80,0));m.owner=owner;m.ground=false;m.id=new UUID(0,i+1);many.add(m);}
        for(int t=2000;t<3000;t++){w.time=t;for(var m:many)EquipmentNotices.tick(m,false);}
        var times=many.stream().flatMap(m->m.reportTimes.stream()).sorted().toList();require(times.size()==20,"staggering starved a maid");
        for(int i=1;i<times.size();i++)require(times.get(i)-times.get(i-1)>=40,"owner flooded by simultaneous notices");
        var state=new EquipmentNoticePolicy();state.observe(0,false,false,1);state.observe(1,false,false,1);state.spoken(1);
        state.observe(2,false,true,1);require(state.readyEvent(200)==0&&state.readyEvent(201)==EquipmentNoticePolicy.LANDED,"per-maid 200 tick cooldown broken");
    }
}
