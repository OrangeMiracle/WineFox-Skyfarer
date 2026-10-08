package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.config.*;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class RiptidePriorityChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why+" recent="+ReplayWorld.events.stream().skip(Math.max(0,ReplayWorld.events.size()-10)).toList());}
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","5");SkyfarerConfig.DEBUG_LOGGING.set(true);
        require(SkyfarerSettings.extraElytraItems().contains("cataclysm:ignitium_elytra_chestplate"),"default compatibility missing");
        var compatible=new ItemStack("cataclysm:ignitium_elytra_chestplate");compatible.unbreakable=true;
        require(WingInventory.ready(compatible,SkyfarerSettings.extraElytraItems(),s->true),"default ID not recognized");
        require(!WingInventory.ready(compatible,SkyfarerSettings.extraElytraItems(),s->false),"default ID fabricated flight ability");
        require(!WingInventory.recognized(new ItemStack(Items.STONE),SkyfarerSettings.extraElytraItems()),"missing third-party registration matched ordinary item");
        airPriority();launch(true);launch(false);PlannerSession.stop();
        System.out.println("Riptide priority passed: real async selection with rocket cooldown; inventory rain launch with/without fireworks; charge/jump/glide/native impulse; invalid environment rejection; default equivalent list.");
    }
    static void airPriority()throws Exception {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,new Vec3(0,80,0));m.owner=new Player(w,new Vec3(20,80,30));
        m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;m.rain=true;m.velocity=new Vec3(0,-.1,.7);
        m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        var c=new AirCombatController(m);var planning=(AsyncPlanning)field(c,"planning");var heading=(HeadingController)field(c,"heading");var fireworks=(FireworkManager)field(c,"fireworks");
        var last=FireworkManager.class.getDeclaredField("lastBoost");last.setAccessible(true);
        var choose=AirCombatController.class.getDeclaredMethod("tryRiptide",Vec3.class,double.class,boolean.class);choose.setAccessible(true);
        var manager=(RiptidePropulsion)field(c,"riptide");
        for(int t=1;t<180&&!manager.active();t++) {
            w.advance(t);planning.begin(t,"PRIORITY",null,0);heading.beginTick(m);manager.prepare(m,true);last.setLong(fireworks,t);
            choose.invoke(c,new Vec3(0,85,120),85d,false);
        }
        require(manager.active()&&fireworks.cooldown(m)>0&&m.inventory.consumed==0,"rocket cooldown suppressed safe Riptide");
        manager.cancel(m);require(!manager.ready(m),"spent readiness was reused");
        for(int i=0;i<10;i++){w.advance(w.getGameTime()+1);manager.prepare(m,true);}
        require(manager.ready(m),"independent preparation did not refill in 10 ticks");
        m.rain=false;require(!manager.ready(m)&&RiptidePropulsion.strengths(m).isEmpty(),"dry/roofed maid allowed Riptide");
        m.water=true;require(manager.ready(m),"water not eligible");m.water=false;m.rain=true;
        m.spinTicks=1;require(!manager.ready(m),"native spin use constraint ignored");m.spinTicks=0;
        require(manager.ready(m),"native spin ended but artificial delay remained");c.dispose("TEST",false);
    }
    static void launch(boolean rockets)throws Exception {
        var w=new ReplayWorld();w.loadedRadius=256;var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,new Vec3(8,0,8));
        m.rain=true;m.inventory.count=rockets?64:0;m.backpack.stack=new ItemStack(Items.TRIDENT);m.backpack.stack.strength=3;
        new LivingEntity(w,new Vec3(0,6,32));var c=new AirCombatController(m);boolean jumped=false;
        for(int t=1;t<260&&m.offhand.damage==0;t++) {
            w.advance(t);c.tick();if(m.using)m.usedTicks++;
            if(!m.ground&&!m.glide) {
                jumped=true;m.pos=m.pos.add(m.velocity);m.velocity=new Vec3(m.velocity.x*.91,(m.velocity.y-.08)*.98,m.velocity.z*.91);
            } else if(m.glide&&m.offhand.damage==0) {
                var s=EquipmentLifecycleChecks.state(m);EquipmentLifecycleChecks.move(m,FlightPrediction.step(s,m.yaw,m.pitch,180,90,90,.08,false));
            }
        }
        System.out.println(ReplayWorld.events.stream().filter(e->e.contains(m.id.toString())&&(e.contains("RIPTIDE")||e.contains("PROPULSION"))).limit(25).toList());
        require(jumped&&m.glide&&m.offhand.damage==1&&m.spinTicks>0,"legal rain takeoff did not actually release Riptide; state="+c.state());
        require(m.inventory.consumed==0,"rocket won against safe launch Riptide");
        require(!m.using&&m.pushCalls==1,"launch did not finish charge and add impulse");c.dispose("TEST",false);
    }
}
