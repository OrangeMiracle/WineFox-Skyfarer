package dev.winefox.skyfarer.command;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Production scheduler and registry; asynchronous chunk/entity lifecycle supplied by a bounded fake server. */
public final class RemoteWakeChecks {
    private static final UUID OWNER=new UUID(1,1);
    private static UUID id(int i){return new UUID(0,i);}
    private static final class Server implements RemoteWakeScheduler.Backend {
        final Set<UUID> tickets=new HashSet<>(),loaded=new HashSet<>(),assigned=new HashSet<>(),fail=new HashSet<>();
        final Map<UUID,Integer> counts=new HashMap<>();final List<String> events=new ArrayList<>();
        boolean ticketFailure;String qualification;
        public boolean loaded(UUID id){return loaded.contains(id);}
        public boolean ticket(UUID id){if(ticketFailure)return false;tickets.add(id);return true;}
        public String assign(UUID id){if(qualification!=null)return qualification;assigned.add(id);counts.merge(id,1,Integer::sum);return null;}
        public void release(UUID id){tickets.remove(id);}
        public void event(UUID id,long generation,String stage,String reason,int attempted){events.add(id+"/"+generation+"/"+stage+"/"+reason+"/"+attempted);}
        void loadTickets(){for(var id:tickets)if(!fail.contains(id))loaded.add(id);}
        void home(){assigned.clear();loaded.clear();tickets.clear();}
    }
    public static void main(String[] args)throws Exception {
        var scheduler=new RemoteWakeScheduler();var server=new Server();var pair=List.of(id(1),id(2));
        for(int mission=1;mission<=2;mission++) {
            scheduler.offer(OWNER,mission,mission*300,pair,server);
            require(scheduler.active()==2&&server.tickets.size()==2,"both unloaded Home candidates queued");
            server.loadTickets();scheduler.tick(mission*300+1);
            require(server.assigned.containsAll(pair)&&scheduler.active()==0,"both reach assignment");
            scheduler.stop(OWNER,"mission_empty");server.home();
        }
        require(server.counts.get(id(1))==2&&server.counts.get(id(2))==2,"second Home mission reawakens both");

        var candidates=new ArrayList<UUID>();for(int i=1;i<=13;i++)candidates.add(id(i));
        server.fail.add(id(1));server.fail.add(id(2));
        for(int tick=0;tick<=120;tick+=10) {
            scheduler.offer(OWNER,3,1000+tick,candidates.stream().filter(id->!server.assigned.contains(id)).toList(),server);
            require(scheduler.active()<=8,"global wake cap");
            server.loadTickets();scheduler.tick(1001+tick);
        }
        require(server.assigned.size()==11&&!server.tickets.contains(id(1))&&!server.tickets.contains(id(2)),"failed first batch must not starve later candidates; timeout releases tickets");
        require(server.events.stream().anyMatch(s->s.contains("/timeout/")),"timeout diagnostic");
        server.fail.clear();scheduler.offer(OWNER,3,1210,pair,server);server.loadTickets();scheduler.tick(1211);
        require(server.assigned.containsAll(pair),"same-generation timeout is retryable");
        scheduler.stop(OWNER,"finished");server.home();

        for(int t=0;t<40;t+=10)scheduler.offer(OWNER,4,2000+t,candidates,server);
        require(scheduler.active()==8&&server.tickets.size()==8,"8 pending tickets at cap");
        scheduler.offer(new UUID(1,2),1,2040,List.of(id(50)),server);require(scheduler.active()==8,"cap spans owners");
        scheduler.generation(OWNER,5);require(scheduler.active()==0&&server.tickets.isEmpty(),"generation invalidation releases old tickets");
        server.ticketFailure=true;scheduler.offer(OWNER,5,2100,pair,server);
        require(scheduler.active()==0&&server.tickets.isEmpty(),"ticket creation failure releases and does not consume capacity");
        server.ticketFailure=false;scheduler.offer(OWNER,5,2300,pair,server);server.loadTickets();server.qualification="equipment";scheduler.tick(2301);
        require(scheduler.active()==0&&server.tickets.isEmpty(),"qualification failure releases");
        server.qualification=null;server.loaded.clear();scheduler.offer(OWNER,5,2500,pair,server);scheduler.clear();
        require(scheduler.active()==0&&server.tickets.isEmpty(),"server stop cleanup");

        // Actual SavedData round trip, including a stale negative role and unknown legacy record.
        var registry=new SkyfarerMaidRegistry();
        registry.put(new SkyfarerMaidRegistry.Entry(id(1),OWNER,"minecraft:overworld",new BlockPos(-321,70,800),true,true,true,"one"));
        registry.put(new SkyfarerMaidRegistry.Entry(id(2),OWNER,"minecraft:overworld",new BlockPos(450,90,-600),true,true,false,"two"));
        registry.put(new SkyfarerMaidRegistry.Entry(id(3),OWNER,"minecraft:overworld",new BlockPos(460,90,-600),false,false,false,"legacy"));
        var restored=SkyfarerMaidRegistry.load(registry.save(new CompoundTag(),null),null);
        require(restored.owned(OWNER).size()==3&&restored.find(id(1)).equals(registry.find(id(1))),"registry NBT keeps all same-owner records and positions");
        scheduler=new RemoteWakeScheduler();var rebuiltServer=new Server();
        var restoredIds=restored.owned(OWNER).stream().filter(e->DispatchPolicy.discover(e.known(),e.home(),true,false,900,128,1000)).map(SkyfarerMaidRegistry.Entry::id).toList();
        require(DispatchPolicy.discover(true,false,true,false,900,128,1000)&&!DispatchPolicy.discover(true,true,true,false,1001,128,1000),"Home/Follow share creative range; no range bypass");
        scheduler.offer(OWNER,1,0,restoredIds,rebuiltServer);rebuiltServer.loadTickets();scheduler.tick(1);
        scheduler.offer(OWNER,1,10,restoredIds.stream().filter(id->!rebuiltServer.assigned.contains(id)).toList(),rebuiltServer);rebuiltServer.loadTickets();scheduler.tick(11);
        require(rebuiltServer.assigned.size()==3,"rebuilt registry gets fair batches including stale role hints");
        scheduler.clear();rebuiltServer.home();
        System.out.println("Remote wake lifecycle passed: two Home maids, second mission, 13-candidate batches, retry, generations, NBT rebuild, qualification, global cap and cleanup.");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
