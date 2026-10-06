package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.*;

/** Main-thread mailbox, cache and snapshot pump. Workers hold tickets, never this owner. */
public final class PlannerSession {
    public interface Source {
        CollisionSnapshot.Cell capture(long cell);
        CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch);
        default int lastReads(){return 216;}
        default String dimension(){return "unknown";}
        default Object levelIdentity(){return this;}
        default PhysicsContext physics(long tick){return PhysicsContext.VANILLA;}
        default void releasePhysics(){}
        default void failed(String message){}
    }
    public static final class Pending extends RuntimeException {
        private Pending(){super(null,null,false,false);}
    }
    public static final Pending WAIT=new Pending();
    private static int captureBudget=21600,cellBudget=256,captureEpoch,rotation,participants=1;
    private static long epochCaptureNanos;
    private static final Set<PlannerSession> sessions=new LinkedHashSet<>();
    public static long staleResults,cacheHits,capturedBlocks,captureNanos,failures;
    public static String lastFailure="none";
    private static int lastFailureReport=-1000;
    public static void serverTick(){
        captureBudget=21600;cellBudget=256;epochCaptureNanos=0;captureEpoch++;
        var active=sessions.stream().filter(s->!s.closed&&(!s.pending.isEmpty()||!s.requested.isEmpty()||captureEpoch-s.lastQueryEpoch<=2)).toList();
        participants=Math.max(1,active.size());
        for(int i=0;i<active.size();i++)active.get(Math.floorMod(rotation+i,active.size())).pump();
        rotation++;
    }
    public static void stop(){for(var s:List.copyOf(sessions))s.close();PlannerExecutor.shutdown();}
    private record CellEntry(CollisionSnapshot.Cell cell,long tick){}
    private record Cached(PlanningTask task,Object value,long tick,long generation){}
    private record Work(PlanningTask task,PlannerExecutor.Ticket<Object> ticket,long tick,long generation){}
    private record Seed(PlanningTask task,long expires){}
    private final Thread owner=Thread.currentThread();private final Source source;
    private final LinkedHashMap<Long,CellEntry> cells=new LinkedHashMap<>();
    private final Map<Long,Integer> requested=new HashMap<>();
    private final Map<PlanningTask.Slot,Work> pending=new HashMap<>();
    private final Map<PlanningTask.Slot,Seed> seeds=new HashMap<>();
    private final ArrayDeque<Cached> cache=new ArrayDeque<>();
    private long now,generation,config;private Object phase,target;private double horizon=1;
    private boolean closed;
    private String dimension;
    private Object levelIdentity;
    private PhysicsContext physics=PhysicsContext.VANILLA;
    private long physicsAt;
    public PhysicsContext physics(){check();return physics;}
    private int localEpoch=-1,capturedThisEpoch,lastQueryEpoch=-1000;
    private FlightPrediction.State urgent=CollisionSnapshot.point(0,0,0);
    private Map<Long,CollisionSnapshot.Cell> frozen=Map.of();private boolean dirty;
    public PlannerSession(Source source){this.source=source;sessions.add(this);}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Planning mailbox/world capture accessed off owner thread");}
    public void begin(long tick,Object phase,Object target,long config,double horizon){
        check();if(closed){closed=false;sessions.add(this);}
        now=tick;
        String currentDimension=source.dimension();
        Object currentLevel=source.levelIdentity();
        if(levelIdentity!=currentLevel||!Objects.equals(dimension,currentDimension)){
            invalidate();cells.clear();requested.clear();frozen=Map.of();dirty=false;dimension=currentDimension;levelIdentity=currentLevel;
        }
        PhysicsContext captured;
        try{captured=Objects.requireNonNullElse(source.physics(tick),PhysicsContext.VANILLA);}
        catch(RuntimeException failure){captured=PhysicsContext.VANILLA;}
        // Natural effect countdown is represented in every frozen State. Changes of the
        // governing law cancel certificates; a duration refresh also invalidates them.
        if(!physics.after(tick-physicsAt).equals(captured))invalidate();
        physics=captured;physicsAt=tick;
        if(!Objects.equals(this.phase,phase)||!Objects.equals(this.target,target)||this.config!=config){invalidate();this.phase=phase;this.target=target;this.config=config;}
        this.horizon=horizon;pump();
    }
    public void invalidate(){check();generation++;staleResults+=pending.size();for(var work:pending.values())work.ticket.cancel();pending.clear();cache.clear();seeds.clear();}
    public void close(){check();invalidate();cells.clear();requested.clear();frozen=Map.of();source.releasePhysics();physics=PhysicsContext.VANILLA;levelIdentity=null;closed=true;sessions.remove(this);}
    private void pump(){
        check();
        for(var it=pending.entrySet().iterator();it.hasNext();){var work=it.next().getValue();var done=work.ticket.outcome;
            if(done==null)continue;it.remove();
            if(work.generation!=generation){staleResults++;continue;}
            if(done.failure()!=null){
                failures++;String message=done.failure().toString();
                if(!message.equals(lastFailure)||captureEpoch-lastFailureReport>=200){source.failed(message);lastFailureReport=captureEpoch;}
                lastFailure=message;continue;
            }
            if(!done.missing().isEmpty()){
                var task=work.task.unbound();
                int priority=task instanceof PlanningTask.Slope||task instanceof PlanningTask.Funnel?2:task instanceof PlanningTask.Sweep sweep&&sweep.path()==null?2:0;
                for(long cell:done.preferred())request(cell,0);
                // A one-cell halo absorbs the entity's movement during the async round trip.
                // Without it, a moving route can always be one uncaptured cell ahead of its result.
                for(long cell:done.preferred()){
                    int x=CollisionSnapshot.x(cell),y=CollisionSnapshot.y(cell),z=CollisionSnapshot.z(cell);
                    request(CollisionSnapshot.key(x-1,y,z),1);request(CollisionSnapshot.key(x+1,y,z),1);
                    request(CollisionSnapshot.key(x,y-1,z),1);request(CollisionSnapshot.key(x,y+1,z),1);
                    request(CollisionSnapshot.key(x,y,z-1),1);request(CollisionSnapshot.key(x,y,z+1),1);
                }
                for(long cell:done.missing())request(cell,priority);
                // Missing alternative corridors cannot invalidate a selected, fully certified path.
                if(!usable(done.value()))continue;
            }
            Object value=done.value();
            if(value instanceof PlanningTask.FunnelResult fresh){
                for(var old:cache)if(old.value instanceof PlanningTask.FunnelResult prior&&work.task.compatible(old.task)&&coverageValid(prior)
                    &&((PlanningTask.Funnel)work.task.unbound()).input().equals(((PlanningTask.Funnel)old.task.unbound()).input())){
                    // Cold discovery may find a different subset as cells arrive. Existing
                    // legal windows remain stable; fill vacant slots instead of replacing
                    // them with another partially observed sample of the same static sky.
                    var windows=new ArrayList<>(prior.atlas().windows());
                    var coverage=new HashMap<>(prior.coverage());coverage.putAll(fresh.coverage());
                    if(coverage.size()<=640)for(var w:fresh.atlas().windows()){
                        if(windows.size()==AttackFunnelPlanner.MAX_WINDOWS)break;
                        if(windows.stream().noneMatch(p->p.id()==w.id()))windows.add(w);
                    }
                    value=windows.equals(prior.atlas().windows())?prior:
                        new PlanningTask.FunnelResult(new AttackFunnelPlanner.Atlas(windows),coverage);break;
                }
                if(usable(value))cache.removeIf(c->c.task.unbound() instanceof PlanningTask.Funnel);
            }
            cache.addFirst(new Cached(work.task,value,work.tick,work.generation));while(cache.size()>96)cache.removeLast();
        }
        if(localEpoch!=captureEpoch){localEpoch=captureEpoch;capturedThisEpoch=0;}
        int local=Math.max(1,256/participants)-capturedThisEpoch;
        // Refresh gradually; dropping the entire warm volume every expiry period starves control.
        // Keep the selected proof's terrain warm before speculative alternatives. Equal
        // refreshed cells preserve the atlas identity; snapshot publication is not invalidation.
        for(var c:cache)if(c.value instanceof PlanningTask.FunnelResult f)for(long key:f.coverage().keySet()){
            var entry=cells.get(key);if(entry==null||now-entry.tick>=20)request(key,0);
        }
        int refresh=8;
        for(var entry:cells.entrySet()){
            if(refresh==0||requested.size()>=512)break;
            if(now-entry.getValue().tick>=20){requested.putIfAbsent(entry.getKey(),3);refresh--;}
        }
        if(local<=0||cellBudget<=0||captureBudget<216||epochCaptureNanos>=2_000_000||requested.isEmpty())return;
        var nearest=new ArrayList<>(requested.keySet());nearest.sort(Comparator.<Long>comparingInt(requested::get).thenComparingDouble(k->Math.pow(CollisionSnapshot.x(k)*4+2-urgent.x(),2)+Math.pow(CollisionSnapshot.y(k)*4+2-urgent.y(),2)+Math.pow(CollisionSnapshot.z(k)*4+2-urgent.z(),2)));
        var urgentCells=new ArrayDeque<Long>();var backgroundCells=new ArrayDeque<Long>();
        for(long key:nearest)(requested.get(key)==0?urgentCells:backgroundCells).add(key);
        nearest.clear();int turn=0;
        while(!urgentCells.isEmpty()||!backgroundCells.isEmpty()){
            boolean background=urgentCells.isEmpty()||!backgroundCells.isEmpty()&&turn++%3==2;
            nearest.add((background?backgroundCells:urgentCells).removeFirst());
        }
        for(long key:nearest){if(local<=0||cellBudget<=0||captureBudget<216||epochCaptureNanos>=2_000_000)break;requested.remove(key);
            var old=cells.get(key);if(old!=null&&now-old.tick<20)continue;
            long begin=System.nanoTime();var captured=source.capture(key);long elapsed=System.nanoTime()-begin;captureNanos+=elapsed;epochCaptureNanos+=elapsed;
            int reads=source.lastReads();cells.put(key,new CellEntry(captured,now));dirty=true;local--;cellBudget--;capturedThisEpoch++;captureBudget-=reads;capturedBlocks+=reads;
        }
        var protectedCells=new HashSet<Long>();
        for(var c:cache)if(c.value instanceof PlanningTask.FunnelResult f)protectedCells.addAll(f.coverage().keySet());
        while(cells.size()>768){
            var key=cells.keySet().stream().filter(k->!protectedCells.contains(k)).findFirst().orElse(cells.keySet().iterator().next());
            cells.remove(key);dirty=true;
        }
    }
    private void request(long key,int priority){if(requested.size()<512||requested.containsKey(key))requested.merge(key,priority,Math::min);}
    private static boolean usable(Object value){
        if(value instanceof PlanningTask.FunnelResult f)return !f.atlas().windows().isEmpty();
        if(value instanceof PlanningTask.GlideResult g)return g.plan().safe()&&g.forecast().safe();
        if(value instanceof GlidePlanner.PulseReview p)return Double.isFinite(p.cost());
        if(value instanceof PlanningTask.PulseResult p)return Double.isFinite(p.review().cost());
        if(value instanceof Double d)return Double.isFinite(d);
        if(value instanceof SlopeSampler.Estimate s)return s.samples()>16;
        if(value instanceof SweepingAttackPlanner.Rollout r)return r.safe()&&r.hit();
        return value!=null;
    }
    public Object query(PlanningTask task){
        check();lastQueryEpoch=captureEpoch;
        var unbound=task.unbound();
        // Only a currently legal map may pin terrain. A moved target or changed proof
        // must release its old footprint so a cold replacement can use the bounded cache.
        if(unbound instanceof PlanningTask.Funnel)cache.removeIf(c->c.value instanceof PlanningTask.FunnelResult f
            &&(!task.compatible(c.task)||!coverageValid(f)));
        pump();
        if(unbound instanceof PlanningTask.Glide||unbound instanceof PlanningTask.Launch||unbound instanceof PlanningTask.Compact)urgent=task.start();
        for(var cached:cache){
            boolean terrain=unbound instanceof PlanningTask.Slope;
            boolean funnel=unbound instanceof PlanningTask.Funnel;
            boolean geometry=unbound instanceof PlanningTask.Sweep sweep&&sweep.path()==null;
            int maxAge=funnel?(cached.value instanceof PlanningTask.FunnelResult f&&!f.atlas().windows().isEmpty()?Integer.MAX_VALUE:8):terrain?20:geometry?12:4;
            if(cached.generation!=generation||now-cached.tick>maxAge||!task.compatible(cached.task))continue;
            if(cached.value instanceof PlanningTask.FunnelResult f&&!coverageValid(f))continue;
            var a=task.start();var b=cached.task.start();
            // Committing a contact curve is more sensitive than reusing a steering hint.
            // Especially under a long rocket pulse, two ticks of turning can invalidate
            // the hit forecast even though the old general-purpose position bound passes.
            if((unbound instanceof PlanningTask.Rejoin||unbound instanceof PlanningTask.AttackCertificate||unbound instanceof PlanningTask.Capture)&&!contactStateCompatible(a,b,now-cached.tick))continue;
            if(!Objects.equals(a.physics(),b.physics()==null?null:b.physics().after(now-cached.tick)))continue;
            if(a.rocketDelay()!=Math.max(0,b.rocketDelay()-(now-cached.tick)))continue;
            double distance=Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));
            if(!terrain&&!funnel&&(distance>(geometry?16:4)||Math.abs(FlightPrediction.turn(a.yaw(),b.yaw(),180)-a.yaw())>(geometry?90:24)
                ||!geometry&&Math.abs(a.vx()-b.vx())+Math.abs(a.vy()-b.vy())+Math.abs(a.vz()-b.vz())>1.2))continue;
            cacheHits++;
            if(unbound instanceof PlanningTask.Glide&&now>cached.tick&&!pending.containsKey(task.slot()))enqueue(task);
            // Refresh alternatives without a display/control gap. Identical results keep
            // Atlas/Window identity, so Capture jobs are not cancelled by a no-op refresh.
            if(funnel&&cached.value instanceof PlanningTask.FunnelResult f&&f.atlas().windows().size()<AttackFunnelPlanner.MAX_WINDOWS
                &&now-cached.tick>=40&&!pending.containsKey(task.slot()))enqueue(task);
            return cached.value;
        }
        enqueue(task);throw WAIT;
    }
    boolean coverageValid(PlanningTask.FunnelResult result){
        for(var e:result.coverage().entrySet()){
            var current=cells.get(e.getKey());
            if(current==null||now-current.tick>80||!current.cell.equals(e.getValue()))return false;
        }
        return true;
    }
    static boolean contactStateCompatible(FlightPrediction.State a,FlightPrediction.State b,long age){
        return age<=2&&Math.hypot(Math.hypot(a.x()-b.x(),a.z()-b.z()),a.y()-b.y())<=2.5
            &&Math.abs(FlightPrediction.turn(a.yaw(),b.yaw(),180)-a.yaw())<=8
            &&Math.abs(a.pitch()-b.pitch())<=8
            &&Math.abs(a.vx()-b.vx())+Math.abs(a.vy()-b.vy())+Math.abs(a.vz()-b.vz())<=.35;
    }
    private void enqueue(PlanningTask task){
        if(task.unbound() instanceof PlanningTask.Sweep sweep&&sweep.path()==null){
            var seed=seeds.get(task.slot());
            if(seed!=null&&now<=seed.expires&&task.compatible(seed.task))task=seed.task;
            else seeds.put(task.slot(),new Seed(task,now+12));
        }
        var prior=pending.get(task.slot());
        if(prior!=null&&now-prior.tick<=(task.unbound() instanceof PlanningTask.Funnel?40:6)&&task.compatible(prior.task))return;
        if(prior!=null){prior.ticket.cancel();pending.remove(task.slot());}
        // Expired shapes are absent, not silently considered empty. Fetch only cells actually queried.
        if(cells.values().removeIf(c->now-c.tick>80))dirty=true;
        if(dirty){var map=new HashMap<Long,CollisionSnapshot.Cell>();cells.forEach((k,v)->map.put(k,v.cell));frozen=Map.copyOf(map);dirty=false;}
        var unbound=task.unbound();boolean wet=unbound instanceof PlanningTask.Launch l&&l.wet();
        var snapshot=source.snapshot(frozen,wet,unbound instanceof PlanningTask.Launch||unbound instanceof PlanningTask.Funnel);
        if(pending.size()>=24)return;
        var ticket=PlannerExecutor.submit(task,snapshot,horizon);
        if(ticket!=null)pending.put(task.slot(),new Work(task,ticket,now,generation));
    }
    /** Debug never schedules or repeats a forecast. */
    public List<GlidePlanner.TracePoint> trace(){
        check();for(var c:cache)if(c.generation==generation&&now-c.tick<=8){
            if(c.value instanceof PlanningTask.GlideResult g)return g.trace();
            if(c.value instanceof SweepingAttackPlanner.Rollout r)return r.trace();
            if(c.value instanceof SweepingAttackPlanner.Selection s)return s.prediction().trace();
        }return List.of();
    }
    public int pendingCount(){check();return pending.size();}
    public int cellCount(){check();return cells.size();}
    public String detail(){
        check();return "gen="+generation+" cells="+cells.size()+" requested="+requested.size()+" jobs="+pending.size()+" cache="+cache.size()+" "+metrics();
    }
    public long generation(){check();return generation;}
    public static int activeSessions(){return sessions.size();}
    public static String metrics(){return "queue="+PlannerExecutor.queued()+" cache="+cacheHits+" stale="+staleResults+" failed="+failures
        +" cancelled="+PlannerExecutor.cancelled.sum()+" submitted="+PlannerExecutor.submitted.sum()
        +" captureMs="+captureNanos/1_000_000+" workerMs="+PlannerExecutor.workerNanos.sum()/1_000_000;}
}
