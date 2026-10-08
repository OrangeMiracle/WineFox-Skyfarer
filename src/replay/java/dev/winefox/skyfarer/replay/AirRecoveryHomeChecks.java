package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.config.SkyfarerConfig;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Production air decisions/physics; external entities and TLM schedule are not a running game. */
public final class AirRecoveryHomeChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why+" "+ReplayWorld.events.stream().skip(Math.max(0,ReplayWorld.events.size()-8)).toList());}
    static void set(Object object,String field,Object value)throws Exception{var f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
    static EntityMaid maid(ReplayWorld w){
        var m=new EntityMaid(w,new Vec3(0,16,0));m.owner=new Player(w,Vec3.ZERO);m.ground=false;m.glide=true;m.pose=Pose.FALL_FLYING;
        m.rain=true;m.inventory.count=0;m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;m.velocity=new Vec3(0,-.15,.45);return m;
    }
    static void move(EntityMaid m){
        if(m.using)m.usedTicks++;
        var next=FlightPrediction.step(EquipmentLifecycleChecks.state(m),m.yaw,m.pitch,180,90,90,.08,m.world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<m.world.time));
        EquipmentLifecycleChecks.move(m,next);
        if(m.spinTicks>0)m.spinTicks--;
        if(m.getY()<=0){m.pos=new Vec3(m.getX(),0,m.getZ());m.velocity=Vec3.ZERO;m.ground=true;m.glide=false;m.pose=Pose.STANDING;}
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("wfs.replayTickMillis","5");SkyfarerConfig.DEBUG_LOGGING.set(true);
        recover(AirCombatState.APPROACH,true);recover(AirCombatState.REPOSITION,true);recover(AirCombatState.ATTACK_EXIT,true);recover(AirCombatState.LANDING,true);recover(AirCombatState.APPROACH,false);
        home(false);home(true);PlannerSession.stop();
        System.out.println("Air recovery / dry rejection / continuously-loaded 900-block Home local landing and control release passed");
    }
    static void recover(AirCombatState state,boolean wet)throws Exception{
        var w=new ReplayWorld();w.loadedRadius=256;var m=maid(w);m.rain=wet;if(!wet)m.inventory.count=64;
        var target=new LivingEntity(w,new Vec3(0,0,-10));var c=new AirCombatController(m);c.acceptCommand(target);set(c,"state",state);
        if(state==AirCombatState.LANDING){set(c,"returnReason","NO_TARGET_AFTER_LOITER");set(c,"preferred",null);}
        if(state==AirCombatState.ATTACK_EXIT){set(c,"recoveryOrigin",m.position().add(0,0,-4));set(c,"waypoint",m.getEyePosition());set(c,"exitDistanceLimit",0d);}
        boolean recovered=false;
        for(int tick=1;tick<130;tick++){
            w.advance(tick);c.tick();
            if(wet&&m.releaseCalls>0&&!c.riptideActive()){
                require(m.glide&&!m.ground&&m.velocity.horizontalDistance()>.7,"air recovery did not build speed");recovered=true;break;
            }
            if(!wet&&m.inventory.consumed>0){recovered=true;break;}
            require(!m.ground,"landed instead of recovering with live next target");move(m);
        }
        require(recovered,"no recovery selected state="+state+" wet="+wet);
        require(wet?m.releaseCalls==1&&m.inventory.consumed==0:m.releaseCalls==0,"wrong propulsion or invalid rain release");
        System.out.println("air recovery state="+state+" wet="+wet+" release="+m.releaseCalls+" rockets="+m.inventory.consumed+" altitude="+m.getY());c.dispose("TEST",false);
    }
    static void home(boolean initiallyGrounded)throws Exception{
        var w=new ReplayWorld();w.loadedRadius=2048;w.boxes.clear();w.boxes.add(new FlightCorridor.Box(-2048,-20,-2048,2048,0,2048));
        var m=maid(w);m.home=true;m.homePos=new net.minecraft.core.BlockPos(900,0,0);
        if(initiallyGrounded){m.pos=Vec3.ZERO;m.ground=true;m.glide=false;}
        var c=new AirCombatController(m);c.commandHomeReturn();
        require(initiallyGrounded?!c.controlsAir():c.controlsAir()&&m.glide,"unsafe Home handoff");
        int landed=-1;
        for(int tick=1;tick<240;tick++){
            w.advance(tick);c.tick();
            require(m.getX()<100&&m.teleports==0,"WineFox attempted the 900-block Home journey/teleport");
            if(m.ground&&!c.controlsAir()){landed=tick;break;}move(m);
        }
        require(landed>0&&!c.controlsAir(),"Home completion kept managed flight");
        String manager=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/winefox/skyfarer/command/CommandManager.java"));
        require(manager.contains("commandHomeReturn()")&&manager.contains("releaseHomeControl(RECALLS.get")&&!manager.contains("TeleportHelper.teleportToRestrictCenter"),"Home integration still runs a competing teleport scheduler");
        System.out.println("Home kept loaded; initiallyGrounded="+initiallyGrounded+" releasedTick="+landed+" position="+m.pos);c.dispose("TEST",false);
    }
}
