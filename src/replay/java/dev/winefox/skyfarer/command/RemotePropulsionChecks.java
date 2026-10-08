package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.PropulsionCapability;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.replay.ReplayWorld;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual live propulsion gate, scheduler and transit; entity loading/ticking are offline fixtures. */
public final class RemotePropulsionChecks {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","5");dev.winefox.skyfarer.config.SkyfarerConfig.DEBUG_LOGGING.set(true);
        for(boolean creative:new boolean[]{false,true})run(creative);
        // CommandManager depends on the full server. Check both production call sites still use
        // the gate exercised here, rather than pretending this fixture executes ServerTick/Post.
        String manager=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/winefox/skyfarer/command/CommandManager.java"));
        require(manager.split("PropulsionCapability.rejection",-1).length==3,"assignment/grounded order gate disconnected");
        require(!manager.contains("SkyfarerEquipment.fireworks(maid)"),"obsolete inventory-only command gate");
        dev.winefox.skyfarer.async.PlannerSession.stop();
        System.out.println("Remote normal/creative Home Riptide-only qualification, bounded wake, dry rejection, transit and glide handoff passed");
    }
    static void run(boolean creative) {
        var w=new ReplayWorld();w.loadedRadius=2048;
        var m=new EntityMaid(w,Vec3.ZERO);m.owner=new Player(w,Vec3.ZERO);m.home=true;m.rain=true;m.inventory.count=0;
        m.offhand=new ItemStack(Items.TRIDENT);m.offhand.strength=3;
        require(PropulsionCapability.source(m).equals("RIPTIDE")&&PropulsionCapability.rejection(m)==null,"wet Riptide-only Home rejected");
        require(DispatchPolicy.discover(true,true,creative,!creative,24,64,0)&&DispatchPolicy.admitted(true,creative,24,64,0),"Home discovery/admission rejected");
        m.rain=false;require("no_firework_riptide_environment_invalid".equals(PropulsionCapability.rejection(m)),"dry/no firework needs explicit rejection");
        var scheduler=new RemoteWakeScheduler();var tickets=new HashSet<UUID>();var loaded=new HashSet<UUID>();var assigned=new HashSet<UUID>();var events=new ArrayList<String>();
        var backend=new RemoteWakeScheduler.Backend(){
            public boolean loaded(UUID id){return loaded.contains(id);}
            public boolean ticket(UUID id){tickets.add(id);return true;}
            public String assign(UUID id){var rejection=PropulsionCapability.rejection(m);if(rejection==null){assigned.add(id);tickets.remove(id);}return rejection;}
            public void release(UUID id){tickets.remove(id);}
            public void event(UUID id,long generation,String stage,String reason,int attempts){events.add(stage+":"+reason);}
        };
        var id=m.getUUID();var owner=m.owner.getUUID();
        scheduler.offer(owner,1,0,List.of(id),backend);loaded.add(id);scheduler.tick(1);
        require(assigned.isEmpty()&&tickets.isEmpty()&&events.stream().anyMatch(s->s.contains("no_firework_riptide_environment_invalid")),"dry maid accepted or ticket leaked");
        m.rain=true;loaded.clear();scheduler.offer(owner,2,200,List.of(id),backend);loaded.add(id);scheduler.tick(201);
        require(assigned.contains(id)&&tickets.isEmpty()&&scheduler.active()==0,"wet maid could not retry/wake/assign");
        m.owner.pos=new Vec3(8,0,8);var transit=new TransitFlight();var goal=new Vec3(0,0,180);boolean arrived=false;
        for(int tick=1;tick<600;tick++) {
            w.advance(tick);
            if(m.position().distanceTo(goal)<=DispatchPolicy.handoffRange(creative)){
                transit.handoff(m);require(m.glide&&!m.ground,"handoff lost glide");arrived=true;break;
            }
            require(transit.tick(m,goal,creative),"Riptide-only transit failed at "+tick+" pos="+m.pos+" vel="+m.velocity+" "+transit.diagnostics()+" events="+ReplayWorld.events.stream().skip(Math.max(0,ReplayWorld.events.size()-6)).toList());
            if(m.glide){
                var s=new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.velocity.x,m.velocity.y,m.velocity.z,m.yaw,m.pitch);
                var n=FlightPrediction.step(s,m.yaw,m.pitch,180,90,90,.08,w.rockets.stream().anyMatch(r->r.alive&&r.createdAt<w.time));
                m.pos=new Vec3(n.x(),n.y()-m.getEyeHeight(),n.z());m.velocity=new Vec3(n.vx(),n.vy(),n.vz());
            }else if(!m.ground){m.pos=m.pos.add(m.velocity);m.velocity=new Vec3(m.velocity.x*.91,(m.velocity.y-.08)*.98,m.velocity.z*.91);}
            require(m.getY()>=0,"transit hit floor");
            for(var r:w.rockets)if(r.alive&&r.createdAt<w.time&&++r.life>r.lifetime)r.discard();
        }
        transit.close();require(arrived&&m.inventory.count==0&&m.inventory.consumed==0,"remote flight required inventory fireworks");
        require(m.releaseCallbacks>0&&m.offhand.damage>0,"Transit did not select/release legal Riptide");
        m.rain=false;m.inventory.count=1;require(PropulsionCapability.source(m).equals("FIREWORK"),"dry firework fallback missing");
        System.out.println("Home dispatch creative="+creative+" inventoryFireworks=0 wake=accepted transit=arrived glide=preserved");
    }
}
