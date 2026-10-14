package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.ai.CommandAccess;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.compat.tlm.PropulsionCapability;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.replay.ReplayWorld;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real scheduler/transit/providers/workers; native world IO, travel and Home relocation are adapters. */
public final class RemoteRecallChecks {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    record Saved(UUID id,int equipment,Vec3 home,ItemStack heart,ItemStack trident){}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","10");
        try{repeat();contactBoundaries();}finally{PlannerSession.stop();CommandAccess.anchor=m->null;}
    }
    static void contactBoundaries(){
        var world=new ReplayWorld();world.loadedRadius=2048;var goal=new Vec3(0,0,480);CommandAccess.anchor=m->goal;
        var maid=RemoteCometChecks.maid(world,Vec3.ZERO);maid.ground=false;maid.velocity=new Vec3(0,-.0784,0);
        var flight=new TransitFlight();boolean stopped=false;
        try{
            for(int tick=1;tick<=25;tick++){
                world.advance(tick);
                if(!flight.tick(maid,goal,true)){stopped=true;check(flight.diagnostics().contains("GROUND_CONTACT_TIMEOUT"),"unexplained settling failure");break;}
                check(!maid.glide&&maid.pushCalls==0,"settling must not authorize flight");
                // Deliberately withhold native travel; an abnormal entity cannot hold the order forever.
            }
            check(stopped,"native contact wait is unbounded");
        }finally{flight.close();}
        var airborne=RemoteCometChecks.maid(world,new Vec3(0,12,0));airborne.ground=false;airborne.velocity=new Vec3(0,-.05,.8);
        var airFlight=new TransitFlight();
        try{world.advance(40);check(airFlight.tick(airborne,goal,true)&&airborne.glide,"real airborne reload was frozen for ground contact");}
        finally{airFlight.close();}
        check(PlannerSession.activeSessions()==0,"contact test leaked sessions");
        System.out.println("Supported false-ground wait expires safely; genuine airborne reload enters glide immediately; sessions closed");
    }
    static void repeat()throws Exception {
        var world=new ReplayWorld();world.loadedRadius=2048;
        world.boxes.clear();world.boxes.add(new FlightCorridor.Box(-2048,50,-2048,2048,78,2048));
        var goal=new Vec3(3,78,-624);var owner=new Player(world,goal);CommandAccess.anchor=m->goal;
        var saved=new LinkedHashMap<UUID,Saved>();
        for(int kind=0;kind<3;kind++){
            var id=new UUID(76,kind);
            var heart=kind==0?new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()):ItemStack.EMPTY;
            var trident=kind==1?new ItemStack(Items.TRIDENT):ItemStack.EMPTY;if(kind==1)trident.strength=3;
            saved.put(id,new Saved(id,kind,new Vec3(87+kind*4,78,-134),heart,trident));
        }
        var scheduler=new RemoteWakeScheduler();var pending=new HashSet<UUID>();var tickets=new HashSet<UUID>();
        var loaded=new HashMap<UUID,EntityMaid>();var orders=new LinkedHashMap<UUID,TransitFlight>();
        var counts=new HashMap<UUID,Integer>();var history=new ArrayList<String>();
        var backend=new RemoteWakeScheduler.Backend(){
            public boolean loaded(UUID id){return loaded.containsKey(id);}
            public boolean ticket(UUID id){pending.add(id);tickets.add(id);return true;}
            public String assign(UUID id){
                var maid=loaded.get(id);var rejection=PropulsionCapability.rejection(maid);if(rejection!=null)return rejection;
                pending.remove(id);orders.put(id,new TransitFlight());counts.merge(id,1,Integer::sum);return null;
            }
            public void release(UUID id){pending.remove(id);tickets.remove(id);}
            public void event(UUID id,long generation,String stage,String reason,int attempts){history.add(generation+":"+stage+":"+attempts);}
        };
        long now=0;
        for(int mission=1;mission<=3;mission++){
            var arrived=new HashSet<UUID>();
            AutoCloseable blocked=mission==2?RemoteCometChecks.blockWorkers():null;
            try{
                for(int step=1;step<=600&&arrived.size()<saved.size();step++){
                    if(step==17&&blocked!=null){blocked.close();blocked=null;}
                    world.advance(++now);
                    if(step%10==1)scheduler.offer(owner.id,mission,now,saved.keySet().stream().filter(id->!orders.containsKey(id)&&!arrived.contains(id)).toList(),backend);
                    for(var id:List.copyOf(pending))if(!loaded.containsKey(id)){
                        var data=saved.get(id);var maid=new EntityMaid(world,data.home());maid.id=id;maid.owner=owner;maid.home=true;
                        maid.baubles.setStackInSlot(0,data.heart().copy());maid.offhand=data.trident().copy();maid.rain=data.equipment()==1;
                        // Actual log after TLM Home return: ground=false, glide=false,
                        // residual downward/horizontal velocity, feet directly on support.
                        maid.ground=false;maid.glide=false;maid.pose=Pose.STANDING;maid.yaw=180;
                        maid.velocity=new Vec3(-.005924840025542303,-.44749789698341763,-.2860225686985316);
                        loaded.put(id,maid);
                    }
                    scheduler.tick(now);check(scheduler.active()<=8,"unbounded wakes");
                    for(var entry:List.copyOf(orders.entrySet())){
                        var maid=loaded.get(entry.getKey());var flight=entry.getValue();var data=saved.get(maid.id);
                        if(maid.position().distanceTo(goal)<=32){
                            flight.handoff(maid);check(maid.glide,"handoff lost flight");
                            if(data.equipment()==0)check(maid.pushCalls>0&&world.rockets.stream().noneMatch(r->r.maid==maid),"heart did not supply actual thrust or used rockets");
                            if(data.equipment()==1)check(maid.releaseCallbacks>0,"ordinary Riptide never released");
                            check(maid.inventory.consumed==0,"free dispatch consumed inventory");
                            arrived.add(maid.id);orders.remove(maid.id);tickets.remove(maid.id);
                            // Task complete -> native Home destination -> unload. Keep UUID,
                            // equipment data and residual motion; never retain the live entity.
                            maid.pos=data.home();maid.glide=false;maid.pose=Pose.STANDING;maid.ground=false;
                            saved.put(maid.id,new Saved(data.id(),data.equipment(),maid.pos,maid.baubles.getStackInSlot(0).copy(),maid.offhand.copy()));
                            loaded.remove(maid.id);maid.alive=false;world.entities.remove(maid);continue;
                        }
                        boolean initial=!maid.ground&&!maid.glide&&maid.getY()==78;
                        check(flight.tick(maid,goal,true),"mission="+mission+" source="+data.equipment()+" step="+step+" failed "+flight.diagnostics());
                        check(!initial||!maid.glide,"reloaded standing maid entered glide before native ground contact");
                        move(maid);
                    }
                    for(var rocket:world.rockets)if(rocket.alive&&rocket.createdAt<world.time&&++rocket.life>rocket.lifetime)rocket.discard();
                }
                check(arrived.size()==3,"mission "+mission+" incomplete");scheduler.stop(owner.id,"mission_empty");
                check(orders.isEmpty()&&loaded.isEmpty()&&tickets.isEmpty()&&pending.isEmpty()&&scheduler.active()==0&&PlannerSession.activeSessions()==0,"lifecycle leaked after mission "+mission);
                System.out.println("Mission "+mission+": same three UUIDs, eternal/Riptide/firework, restored false ground + residual velocity, transit/handoff/Home/unload passed");
            }finally{if(blocked!=null)blocked.close();orders.values().forEach(TransitFlight::close);scheduler.clear();}
        }
        check(counts.values().stream().allMatch(n->n==3),"repeat admission lost");
        for(int gen=1;gen<=3;gen++){String prefix=gen+":";check(history.stream().filter(s->s.equals(prefix+"wake_queued:1")).count()==3,"attempts leaked across missions");}
    }
    static void move(EntityMaid maid){
        if(maid.glide){
            var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),maid.velocity.x,maid.velocity.y,maid.velocity.z,maid.yaw,maid.pitch);
            boolean rocket=maid.world.rockets.stream().anyMatch(r->r.alive&&r.maid==maid&&r.createdAt<maid.world.time);
            var next=FlightPrediction.step(start,maid.yaw,maid.pitch,180,90,90,.08,rocket);
            check(new CorridorProbe(maid).clear(start,next,0,0),"flying body collision");
            maid.pos=new Vec3(next.x(),next.y()-maid.getEyeHeight(),next.z());maid.velocity=new Vec3(next.vx(),next.vy(),next.vz());
        }else if(!maid.ground){
            var next=maid.pos.add(maid.velocity);
            // Native standing gravity/collision response, no flight authority or teleport.
            if(next.y<=78){maid.pos=new Vec3(next.x,78,next.z);maid.ground=true;maid.velocity=new Vec3(maid.velocity.x*.546,-.0784,maid.velocity.z*.546);}
            else {maid.pos=next;maid.velocity=new Vec3(maid.velocity.x*.91,(maid.velocity.y-.08)*.98,maid.velocity.z*.91);}
        }else maid.velocity=new Vec3(maid.velocity.x*.546,-.0784,maid.velocity.z*.546);
        check(maid.pos.y>=78,"fell through floor");
    }
}
