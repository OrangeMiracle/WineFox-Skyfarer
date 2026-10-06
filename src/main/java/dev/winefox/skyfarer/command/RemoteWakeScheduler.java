package dev.winefox.skyfarer.command;

import java.util.*;

/** Server-thread, bounded discovery. Attempts expire; a failed UUID never blocks its peers. */
public final class RemoteWakeScheduler {
    public static final int LIMIT=8, BATCH=2, TIMEOUT=100, RETRY=200;
    public interface Backend {
        boolean loaded(UUID id);
        boolean ticket(UUID id);
        /** null means the loaded entity received an order (possibly earlier in its entity tick). */
        String assign(UUID id);
        void release(UUID id);
        void event(UUID id,long generation,String stage,String reason,int attempted);
    }
    private record Attempt(long generation,long retryAt,long sequence,int count) {}
    private record Wake(UUID owner,long generation,long expires,Backend backend) {}
    private final Map<UUID,Wake> wakes=new LinkedHashMap<>();
    private final Map<UUID,Map<UUID,Attempt>> attempts=new HashMap<>();
    private final Map<UUID,Long> generations=new HashMap<>();
    private long sequence;
    public int active(){return wakes.size();}
    public boolean contains(UUID id){return wakes.containsKey(id);}
    public void generation(UUID owner,long generation) {
        if(Objects.equals(generations.get(owner),generation))return;
        stop(owner,"generation_changed");generations.put(owner,generation);
    }
    public void tick(long now) {
        for(var entry:new ArrayList<>(wakes.entrySet())) {
            var id=entry.getKey();var w=entry.getValue();
            if(w.backend.loaded(id)) {
                emit(id,w,"entity_loaded","");
                String reason=w.backend.assign(id);
                emit(id,w,reason==null?"assignment_accepted":"qualification_failed",reason==null?"":reason);
                finish(id,w,reason==null,"loaded");
            } else if(now>=w.expires) {emit(id,w,"timeout","entity_not_loaded");finish(id,w,false,"timeout");}
        }
    }
    public void offer(UUID owner,long generation,long now,List<UUID> candidates,Backend backend) {
        generation(owner,generation);
        var history=attempts.computeIfAbsent(owner,k->new HashMap<>());
        // Do not retain UUIDs which have disappeared from the registry/candidate set forever.
        history.keySet().removeIf(id->!candidates.contains(id)&&!wakes.containsKey(id));
        var ordered=new ArrayList<>(new LinkedHashSet<>(candidates));
        ordered.sort(Comparator.<UUID>comparingLong(id->history.containsKey(id)?history.get(id).sequence:Long.MIN_VALUE).thenComparing(UUID::toString));
        int budget=BATCH;
        for(var id:ordered) {
            var old=history.get(id);
            if(wakes.containsKey(id)||old!=null&&old.retryAt>now)continue;
            if(wakes.size()>=LIMIT||budget==0)break; // Unattempted candidates stay eligible next batch.
            budget--;
            var attempt=new Attempt(generation,now+RETRY,++sequence,old==null?1:old.count+1);history.put(id,attempt);
            var wake=new Wake(owner,generation,now+TIMEOUT,backend);
            emit(id,wake,"wake_queued","attempted_until="+attempt.retryAt);
            if(!backend.ticket(id)) {emit(id,wake,"ticket_failed","");backend.release(id);emit(id,wake,"released","ticket_failed");continue;}
            wakes.put(id,wake);emit(id,wake,"ticket_created","");
        }
    }
    private void emit(UUID id,Wake w,String stage,String reason) {
        var attempt=attempts.getOrDefault(w.owner,Map.of()).get(id);
        w.backend.event(id,w.generation,stage,reason,attempt==null?0:attempt.count);
    }
    private void finish(UUID id,Wake w,boolean assigned,String reason) {
        wakes.remove(id);
        // Accepted creative assignments replace the discovery ticket with a rolling flight window.
        if(!assigned)w.backend.release(id);
        emit(id,w,"released",assigned?"transferred_to_order":reason);
    }
    public void remove(UUID id,String reason) {
        var w=wakes.get(id);if(w!=null)finish(id,w,false,reason);
    }
    public void stop(UUID owner,String reason) {
        for(var e:new ArrayList<>(wakes.entrySet()))if(e.getValue().owner.equals(owner))finish(e.getKey(),e.getValue(),false,reason);
        attempts.remove(owner);generations.remove(owner);
    }
    public void clear() {for(var owner:new ArrayList<>(generations.keySet()))stop(owner,"server_stop");}
}
