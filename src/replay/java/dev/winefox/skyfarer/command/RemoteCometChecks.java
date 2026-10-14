package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.ai.CommandAccess;
import dev.winefox.skyfarer.async.PlannerSession;
import dev.winefox.skyfarer.item.CometProgress;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.replay.ReplayWorld;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real TransitFlight/resolver/planner. World, entity reconstruction and ticking are adapters. */
public final class RemoteCometChecks {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static EntityMaid maid(ReplayWorld world,Vec3 pos){
        var maid=new EntityMaid(world,pos);maid.home=true;maid.owner=new Player(world,pos);
        maid.baubles.setStackInSlot(0,new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get()));
        maid.inventory.count=64;return maid;
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","10");
        try{rejectedDeparture();wakeBatch();cancelAndRebuild();delayedTimeout();}
        finally{PlannerSession.stop();CommandAccess.anchor=m->null;}
    }
    static void rejectedDeparture(){
        var world=new ReplayWorld();world.loadedRadius=2048;
        // There is headroom to request a jump, but no certified route can climb through
        // this ceiling to the requested height. A missing launch is a legitimate result.
        world.boxes.add(new FlightCorridor.Box(-256,2.2,-256,256,8,256));
        var maid=maid(world,Vec3.ZERO);var goal=new Vec3(0,0,480);CommandAccess.anchor=m->goal;
        var flight=new TransitFlight();boolean rejected=false;
        try{
            for(int tick=1;tick<=120;tick++){
                world.advance(tick);
                if(!flight.tick(maid,goal,true)){rejected=true;System.out.println("Rejected launch safely at tick "+tick+": "+flight.diagnostics());break;}
                check(maid.ground,"uncertified jump under ceiling");
            }
            check(rejected,"unsafe departure never released its order");
            check(maid.inventory.consumed==0&&world.rockets.isEmpty(),"exclusive heart fell back to fireworks");
        }finally{flight.close();}
        check(PlannerSession.activeSessions()==0,"failed departure leaked planner");
    }
    static void wakeBatch()throws Exception {
        var world=new ReplayWorld();world.loadedRadius=2048;
        world.boxes.clear();world.boxes.add(new FlightCorridor.Box(-2048,50,-2048,2048,77,2048));
        var goal=new Vec3(3,77,-624);var owner=new Player(world,goal);CommandAccess.anchor=m->goal;
        var saved=new LinkedHashMap<UUID,ItemStack[]>();var oldEntities=new HashMap<UUID,EntityMaid>();
        for(int i=0;i<5;i++){
            var old=maid(world,new Vec3(88+i*3,77,-132));old.owner=owner;old.yaw=180;
            old.baubles.setStackInSlot(1,new ItemStack(WineFoxSkyfarer.COMET_HEART.get()));CometProgress.increment(old.baubles.getStackInSlot(1));
            saved.put(old.id,new ItemStack[]{old.baubles.getStackInSlot(0).copy(),old.baubles.getStackInSlot(1).copy()});oldEntities.put(old.id,old);
            old.alive=false;world.entities.remove(old);
        }
        var loaded=new HashMap<UUID,EntityMaid>();var orders=new LinkedHashMap<UUID,TransitFlight>();
        var discoveryTickets=new HashSet<UUID>();var orderTickets=new HashSet<UUID>();var arrived=new HashSet<UUID>();
        var scheduler=new RemoteWakeScheduler();
        var backend=new RemoteWakeScheduler.Backend(){
            public boolean loaded(UUID id){return loaded.containsKey(id);}
            public boolean ticket(UUID id){discoveryTickets.add(id);return true;}
            public String assign(UUID id){
                var maid=loaded.get(id);var rejected=PropulsionCapability.rejection(maid);if(rejected!=null)return rejected;
                check(CometBaubles.virtual(maid)&&!CometBaubles.saving(maid)&&CometProgress.read(maid.baubles.getStackInSlot(1))==1,"restored native slots/progress wrong");
                check(maid!=oldEntities.get(id),"reused unloaded entity");
                discoveryTickets.remove(id);orderTickets.add(id);orders.put(id,new TransitFlight());return null;
            }
            public void release(UUID id){discoveryTickets.remove(id);}
            public void event(UUID id,long generation,String stage,String reason,int attempts){}
        };
        AutoCloseable blocked=blockWorkers();
        try{
            for(int tick=1;tick<=420&&arrived.size()<saved.size();tick++){
                if(tick==17){blocked.close();blocked=null;}
                world.advance(tick);
                if(tick%10==1)scheduler.offer(owner.id,1,tick,saved.keySet().stream().filter(id->!orders.containsKey(id)&&!arrived.contains(id)).toList(),backend);
                for(var id:List.copyOf(discoveryTickets)){
                    var old=oldEntities.get(id);var maid=maid(world,old.pos);maid.id=id;maid.owner=owner;maid.yaw=180;
                    maid.baubles.setStackInSlot(0,saved.get(id)[0].copy());maid.baubles.setStackInSlot(1,saved.get(id)[1].copy());loaded.put(id,maid);
                }
                scheduler.tick(tick);check(scheduler.active()<=8&&discoveryTickets.size()<=8,"wake cap exceeded");
                for(var entry:List.copyOf(orders.entrySet())){
                    var maid=loaded.get(entry.getKey());var flight=entry.getValue();
                    if(maid.position().distanceTo(goal)<=32){
                        flight.handoff(maid);check(maid.glide&&!maid.ground,"handoff lost glide");
                        arrived.add(maid.id);orders.remove(maid.id);orderTickets.remove(maid.id);continue;
                    }
                    check(flight.tick(maid,goal,true),"open-air Comet transit rejected at "+tick+" "+flight.diagnostics());
                    if(tick<=16)check(maid.pushCalls==0&&maid.ground,"worker blackout authorized propulsion/jump");
                    move(maid);check(maid.inventory.consumed==0&&maid.random.draws==0&&world.rockets.isEmpty(),"everburning transit used lower source");
                }
            }
            check(arrived.size()==5&&orders.isEmpty()&&discoveryTickets.isEmpty()&&orderTickets.isEmpty(),"wake batch did not arrive/clean up");
            check(loaded.values().stream().allMatch(m->m.pushCalls>0),"virtual propulsion was never executed after reload");
        }finally{
            if(blocked!=null)blocked.close();scheduler.clear();orders.values().forEach(TransitFlight::close);
        }
        check(PlannerSession.activeSessions()==0,"completed batch leaked sessions");
        System.out.println("Five reconstructed Home maids: native slots retained, 2-per-batch wake, 16-tick worker blackout, 480-block creative transit, virtual thrust, zero fireworks, airborne handoff and closed sessions passed");
    }
    static void move(EntityMaid maid){
        var old=maid.position();
        if(maid.glide){
            var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),maid.velocity.x,maid.velocity.y,maid.velocity.z,maid.yaw,maid.pitch);
            var next=FlightPrediction.step(start,maid.yaw,maid.pitch,180,90,90,.08,false);
            check(new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(start,next,0,0),"transit body collision");
            maid.pos=new Vec3(next.x(),next.y()-maid.getEyeHeight(),next.z());maid.velocity=new Vec3(next.vx(),next.vy(),next.vz());
        }else if(!maid.ground){maid.pos=old.add(maid.velocity);maid.velocity=new Vec3(maid.velocity.x*.91,(maid.velocity.y-.08)*.98,maid.velocity.z*.91);}
        check(maid.pos.y>=77,"transit hit floor");
    }
    static AutoCloseable blockWorkers()throws Exception {
        var method=dev.winefox.skyfarer.replay.TrajectoryContinuityChecks.class.getDeclaredMethod("blockWorkers");method.setAccessible(true);
        return (AutoCloseable)method.invoke(null);
    }
    static void cancelAndRebuild()throws Exception {
        var world=new ReplayWorld();var old=maid(world,Vec3.ZERO);var flight=new TransitFlight();var goal=new Vec3(0,0,480);
        CommandAccess.anchor=m->goal;
        try(var blocked=blockWorkers()){
            world.advance(1);check(flight.tick(old,goal,true),"cold launch must wait");check(PlannerSession.activeSessions()==1,"missing pending session");
            var replacement=maid(world,Vec3.ZERO);replacement.id=old.id;old.alive=false;
            world.advance(2);check(flight.tick(replacement,goal,true),"reconstructed entity failed to wait");
            check(PlannerSession.activeSessions()==1&&old.pushCalls==0,"old entity session survived replacement");
            flight.close();flight.close();check(PlannerSession.activeSessions()==0,"cancel not idempotent/closed");
        }
        world.advance(3);check(old.pushCalls==0,"cancelled worker touched old entity");
        System.out.println("Pending order cancellation / same-UUID entity replacement close old sessions and providers passed");
    }
    static void delayedTimeout()throws Exception {
        var world=new ReplayWorld();var maid=maid(world,Vec3.ZERO);var flight=new TransitFlight();var goal=new Vec3(0,0,480);
        CommandAccess.anchor=m->goal;boolean stopped=false;
        try(var blocked=blockWorkers()){
            for(int tick=1;tick<=110;tick++){
                world.advance(tick);
                if(!flight.tick(maid,goal,true)){
                    stopped=true;check(flight.diagnostics().contains("LAUNCH_WAIT_TIMEOUT"),"pending treated as certified rejection");break;
                }
                check(maid.ground&&maid.pushCalls==0,"pending worker launch changed physics");
            }
            check(stopped,"endless pending ground wait");flight.close();
        }finally{flight.close();}
        check(PlannerSession.activeSessions()==0&&world.rockets.isEmpty(),"timeout leaked planner or spawned fallback");
        System.out.println("Worker blackout exceeding 100 game ticks: bounded ground wait, no unsafe jump, no fallback, clean cancellation passed");
    }
}
