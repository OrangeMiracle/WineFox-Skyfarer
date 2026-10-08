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
                int priority=task instanceof PlanningTask.Slope?2:task instanceof PlanningTask.Sweep sweep&&sweep.path()==null?2:0;
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
            cache.addFirst(new Cached(work.task,done.value(),work.tick,work.generation));while(cache.size()>96)cache.removeLast();
            if(done.value() instanceof SweepingAttackPlanner.Search search&&search.selection()==null)seeds.remove(work.task.slot());
        }
        if(localEpoch!=captureEpoch){localEpoch=captureEpoch;capturedThisEpoch=0;}
        int local=Math.max(1,256/participants)-capturedThisEpoch;
        // Refresh gradually; dropping the entire warm volume every expiry period starves control.
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
        for(long key:nearest){if(local<=0||cellBudget<=0||captureBudget<216||epochCaptureNanos>=2_000_000)break;int priority=requested.remove(key);
            var old=cells.get(key);if(old!=null&&now-old.tick<(priority==0?8:20))continue;
            long begin=System.nanoTime();var captured=source.capture(key);long elapsed=System.nanoTime()-begin;captureNanos+=elapsed;epochCaptureNanos+=elapsed;
            int reads=source.lastReads();cells.put(key,new CellEntry(captured,now));dirty=true;local--;cellBudget--;capturedThisEpoch++;captureBudget-=reads;capturedBlocks+=reads;
        }
        while(cells.size()>768){cells.remove(cells.keySet().iterator().next());dirty=true;}
    }
    private void request(long key,int priority){if(requested.size()<512||requested.containsKey(key))requested.merge(key,priority,Math::min);}
    private static boolean usable(Object value){
        if(value instanceof SweepingAttackPlanner.Search s)return s.selection()!=null;
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
        if(task.unbound() instanceof PlanningTask.Emergency e)seedEmergency(e);
        pump();
        var unbound=task.unbound();
        if(unbound instanceof PlanningTask.Glide||unbound instanceof PlanningTask.Launch||unbound instanceof PlanningTask.Compact)urgent=task.start();
        for(var cached:cache){
            boolean terrain=unbound instanceof PlanningTask.Slope;
            boolean geometry=unbound instanceof PlanningTask.Sweep sweep&&sweep.path()==null;
            boolean failedMoving=geometry&&cached.value instanceof SweepingAttackPlanner.Search search&&search.selection()==null
                &&((PlanningTask.Sweep)unbound).aim()!=null&&Math.hypot(((PlanningTask.Sweep)unbound).aim().vx(),((PlanningTask.Sweep)unbound).aim().vz())>.025;
            if(cached.generation!=generation||now-cached.tick>(terrain?20:failedMoving?3:geometry?24:4)||!task.compatible(cached.task))continue;
            var a=task.start();var b=cached.task.start();
            // Committing a contact curve is more sensitive than reusing a steering hint.
            // Especially under a long rocket pulse, two ticks of turning can invalidate
            // the hit forecast even though the old general-purpose position bound passes.
            if((unbound instanceof PlanningTask.Rejoin||unbound instanceof PlanningTask.AttackCertificate)&&!contactStateCompatible(a,b,now-cached.tick))continue;
            if(!Objects.equals(a.physics(),b.physics()==null?null:b.physics().after(now-cached.tick)))continue;
            if(a.rocketDelay()!=Math.max(0,b.rocketDelay()-(now-cached.tick)))continue;
            // Emergency results are proposals only; recertifyEmergency checks the current state in full.
            if(unbound instanceof PlanningTask.Emergency){cacheHits++;if(!pending.containsKey(task.slot()))enqueue(task);return cached.value;}
            double distance=Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));
            if(!terrain&&(distance>(geometry?16:4)||Math.abs(FlightPrediction.turn(a.yaw(),b.yaw(),180)-a.yaw())>(geometry?90:24)
                ||!geometry&&Math.abs(a.vx()-b.vx())+Math.abs(a.vy()-b.vy())+Math.abs(a.vz()-b.vz())>1.2))continue;
            cacheHits++;
            if(unbound instanceof PlanningTask.Glide&&now>cached.tick&&!pending.containsKey(task.slot()))enqueue(task);
            return cached.value;
        }
        enqueue(task);throw WAIT;
    }
    /** Reuse the time-aligned suffix of a certified feedback rollout, never its old first frame.
     * This is independent of ordinary cache tolerances and always refreshes in the background. */
    public TrajectoryCommitment.Proof continuation(PlanningTask task) {
        check();lastQueryEpoch=captureEpoch;urgent=task.start();pump();
        var wanted=(PlanningTask.Sweep)task.unbound();
        for(var cached:cache) {
            if(cached.generation!=generation || !(cached.task.unbound() instanceof PlanningTask.Sweep old)
                || old.path()==null || !(cached.value instanceof SweepingAttackPlanner.Rollout rollout))continue;
            if(!sameBounds(task,cached.task))continue;
            var proof=new TrajectoryCommitment.Proof(cached.tick,old.path(),old.aim(),old.rocket(),old.gravity(),old.rate(),old.minimum(),old.exiting(),rollout);
            if(!proof.matches(now,wanted.start(),wanted.path(),wanted.aim(),wanted.rocket(),wanted.gravity(),wanted.rate(),wanted.minimum(),wanted.exiting()))continue;
            cacheHits++;enqueue(task);return proof;
        }
        enqueue(task);return null;
    }
    private static boolean sameBounds(PlanningTask a,PlanningTask b) {
        if(a instanceof PlanningTask.Bounded x)return b instanceof PlanningTask.Bounded y&&x.bounds().equals(y.bounds());
        return !(b instanceof PlanningTask.Bounded);
    }
    /** Refresh only cells touched by the selected route, ahead of the strict 20-tick recheck.
     * The empty snapshot is a numeric body-volume enumerator, never a safety certificate. */
    public void retainTrace(List<GlidePlanner.TracePoint> trace) {
        check();var volume=source.snapshot(Map.of(),false,false).space(new java.util.concurrent.atomic.AtomicBoolean());
        volume.trackTouched();
        for(int i=1;i<trace.size();i++)volume.clear(trace.get(i-1).state(),trace.get(i).state(),trace.get(i).side(),trace.get(i).vertical());
        for(long key:volume.touched()){var cell=cells.get(key);if(cell==null||now-cell.tick>=8)request(key,0);}
    }
    /** One already-selected route, at most 160 integration steps. No search, no world reads,
     * no new propulsion. Used at handoffs or when actual motion no longer matches a suffix. */
    public TrajectoryCommitment.Proof certifyContinuation(PlanningTask task) {
        check();var sweep=(PlanningTask.Sweep)task.unbound();
        var map=new HashMap<Long,CollisionSnapshot.Cell>();
        cells.forEach((k,v)->{if(now-v.tick<=20)map.put(k,v.cell);});
        var space=source.snapshot(Map.copyOf(map),false,false).space(new java.util.concurrent.atomic.AtomicBoolean());
        space.trackTouched();
        var rollout=(SweepingAttackPlanner.Rollout)task.compute(space);
        for(long key:space.missing())request(key,0);
        for(long key:space.touched()){var cell=cells.get(key);if(cell!=null&&now-cell.tick>=8)request(key,0);}
        // A successful strict rollout may have missing optional roomy-clearance samples.
        // Its body/contact/exit proof succeeded; those scoring probes cannot revoke it.
        continuationReason=rollout.safe()?"CURRENT_CERTIFIED":!space.missing().isEmpty()?"SNAPSHOT_MISSING":"CURRENT_PHYSICS_REJECTED";
        if(!rollout.safe()&&!space.missing().isEmpty())return null;
        return new TrajectoryCommitment.Proof(now,sweep.path(),sweep.aim(),sweep.rocket(),sweep.gravity(),sweep.rate(),sweep.minimum(),sweep.exiting(),rollout);
    }
    private String continuationReason="NONE";
    public String continuationReason(){return continuationReason;}
    static boolean contactStateCompatible(FlightPrediction.State a,FlightPrediction.State b,long age){
        return age<=2&&Math.hypot(Math.hypot(a.x()-b.x(),a.z()-b.z()),a.y()-b.y())<=2.5
            &&Math.abs(FlightPrediction.turn(a.yaw(),b.yaw(),180)-a.yaw())<=8
            &&Math.abs(a.pitch()-b.pitch())<=8
            &&Math.abs(a.vx()-b.vx())+Math.abs(a.vy()-b.vy())+Math.abs(a.vz()-b.vz())<=.35;
    }
    /** Seed the already threatened momentum corridor first. Otherwise a cold discovery pass
     * can spend its whole reaction window learning that the wall seen by the live guard exists. */
    private void seedEmergency(PlanningTask.Emergency e){
        urgent=e.start();var s=e.start();
        for(int tick=0;tick<32;tick++){
            for(double x:new double[]{s.x()-.55,s.x()+.55})for(double y:new double[]{s.y()-.55,s.y()+.35})for(double z:new double[]{s.z()-.55,s.z()+.55}){
                long key=CollisionSnapshot.key((int)Math.floor(x/4),(int)Math.floor(y/4),(int)Math.floor(z/4));
                var cell=cells.get(key);if(cell==null||now-cell.tick>=20)request(key,0);
            }
            s=FlightPrediction.step(s,e.start().yaw(),e.start().pitch(),45,20,20,e.gravity(),RocketProfile.active(tick,e.active(),e.start()));
        }
    }
    /** A last-resort ignition cannot rely on a worker's old velocity. Recheck only its selected
     * attitude against the immutable, already captured cells; no world queries/candidate search here. */
    public EmergencyAvoidancePlanner.Plan recertifyEmergency(FlightPrediction.State start,EmergencyAvoidancePlanner.Plan candidate,int active,double gravity){
        check();if(candidate==null)return null;
        var map=new HashMap<Long,CollisionSnapshot.Cell>();
        cells.forEach((k,v)->{if(now-v.tick<=20)map.put(k,v.cell);});
        var space=source.snapshot(Map.copyOf(map),false,false).space(new java.util.concurrent.atomic.AtomicBoolean());
        if(candidate.duration()>=0){
            var glide=EmergencyAvoidancePlanner.choose(start,active,List.of(),gravity,space);
            if(glide!=null)return glide;
        }
        var result=EmergencyAvoidancePlanner.certify(start,candidate.yaw(),candidate.pitch(),candidate.duration(),active,gravity,space);
        for(long key:space.missing())request(key,0);
        return result;
    }
    public RiptidePlanner.Plan recertifyRiptide(FlightPrediction.State start,RiptidePlanner.Plan plan,int elapsed,boolean wet){
        check();if(plan==null)return null;
        var map=new HashMap<Long,CollisionSnapshot.Cell>();
        cells.forEach((k,v)->{if(now-v.tick<=20)map.put(k,v.cell);});
        var space=source.snapshot(Map.copyOf(map),wet,true).space(new java.util.concurrent.atomic.AtomicBoolean());
        space.trackTouched();
        var result=RiptidePlanner.certify(start,plan,elapsed,physics().gravity(),space,wet?space:SurfaceLaunchPlanner.DRY);
        for(long key:space.missing())request(key,0);
        // Active thrust must refresh its own remaining corridor before the 20-tick expiry.
        // Enqueue only: existing global/local capture budgets still govern world access.
        for(long key:space.touched()){var entry=cells.get(key);if(entry!=null&&now-entry.tick>=8)request(key,0);}
        riptideRejection=result!=null?"NONE":!space.missing().isEmpty()?"SNAPSHOT_MISSING:"+space.missing().size():"TRAJECTORY_REJECTED";
        return result;
    }
    private String riptideRejection="NONE";
    public String riptideRejection(){check();return riptideRejection;}
    /** Rejoin results are proposals until this bounded CURRENT-state simulation succeeds.
     * No candidate search or live world/entity access; all random rocket lifetimes remain checked. */
    public PlanningTask.RejoinResult recertifyRejoin(PlanningTask.Rejoin task,PlanningTask.RejoinResult proposal,CollisionSnapshot.Bounds bounds){
        check();if(proposal==null){rejoinReason="NO_WORKER_PROPOSAL";return null;}
        var map=new HashMap<Long,CollisionSnapshot.Cell>();
        cells.forEach((k,v)->{if(now-v.tick<=20)map.put(k,v.cell);});
        var space=source.snapshot(Map.copyOf(map),false,task.launchBody()).space(new java.util.concurrent.atomic.AtomicBoolean());
        GlidePlanner.Corridor corridor=bounds==null?space:(a,b,side,vertical)->Math.hypot(b.x()-bounds.x(),b.z()-bounds.z())<=bounds.radius()&&space.clear(a,b,side,vertical);
        SweepingAttackPlanner.Contact contact=(s,a,t)->space.ray(s.x(),s.y(),s.z(),a.x()+a.vx()*t,a.bottom()+a.height()*.5+a.vy()*t,a.z()+a.vz()*t);
        int lo=proposal.duration()<0?task.rocket():RocketProfile.minimumTicks(proposal.duration());
        int hi=proposal.duration()<0?task.rocket():RocketProfile.maximumTicks(proposal.duration());
        SweepingAttackPlanner.Rollout chosen=null;
        for(int life=lo;life<=hi;life++){
            var start=proposal.duration()<0?task.start():task.start().withRocketDelay(1);
            var result=SweepingAttackPlanner.simulate(start,proposal.selection().path(),task.aim(),life,task.gravity(),task.rate(),task.minimum(),false,corridor,contact,120);
            if(!result.safe()||!result.hit()){
                rejoinReason=!space.missing().isEmpty()?"SNAPSHOT_MISSING:"+space.missing().size():!result.hit()?"CURRENT_CONTACT_MISS:"+result.miss():"CURRENT_EXIT_UNSAFE";
                for(long key:space.missing())request(key,0);return null;
            }
            chosen=result;
        }
        rejoinReason="CURRENT_STRIKE_EXIT_CERTIFIED";
        retainTrace(chosen.trace());
        return new PlanningTask.RejoinResult(new SweepingAttackPlanner.Selection(proposal.selection().path(),chosen),proposal.duration());
    }
    private String rejoinReason="NOT_REQUESTED";
    public String rejoinReason(){check();return rejoinReason;}
    private void enqueue(PlanningTask task){
        if(task.unbound() instanceof PlanningTask.Sweep sweep&&sweep.path()==null){
            var seed=seeds.get(task.slot());
            if(seed!=null&&now<=seed.expires&&task.compatible(seed.task))task=seed.task;
            else seeds.put(task.slot(),new Seed(task,now+24));
        }
        var prior=pending.get(task.slot());
        boolean following=task.unbound() instanceof PlanningTask.Sweep s&&s.path()!=null;
        boolean geometry=task.unbound() instanceof PlanningTask.Sweep s&&s.path()==null;
        boolean sameContinuation=following&&prior!=null&&sameBounds(task,prior.task)
            &&prior.task.unbound() instanceof PlanningTask.Sweep old&&old.path()==((PlanningTask.Sweep)task.unbound()).path()
            &&old.exiting()==((PlanningTask.Sweep)task.unbound()).exiting();
        // Let a route refresh finish despite natural target/rocket countdown. Its delivered
        // proof still has to pass time-aligned state, aim, force and physics checks above.
        if(prior!=null&&now-prior.tick<=(geometry?24:following?TrajectoryCommitment.MAX_PROOF_AGE:6)
            &&(task.compatible(prior.task)||sameContinuation))return;
        if(prior!=null){prior.ticket.cancel();pending.remove(task.slot());}
        // Expired shapes are absent, not silently considered empty. Fetch only cells actually queried.
        if(cells.values().removeIf(c->now-c.tick>80))dirty=true;
        if(dirty){var map=new HashMap<Long,CollisionSnapshot.Cell>();cells.forEach((k,v)->map.put(k,v.cell));frozen=Map.copyOf(map);dirty=false;}
        var unbound=task.unbound();boolean wet=unbound instanceof PlanningTask.Launch l&&l.wet()||unbound instanceof PlanningTask.Propulsion p&&p.wet();
        var snapshot=source.snapshot(frozen,wet,unbound instanceof PlanningTask.Launch||unbound instanceof PlanningTask.LaunchAttack||unbound instanceof PlanningTask.Propulsion p&&p.demand().departure()||unbound instanceof PlanningTask.Rejoin r&&r.launchBody());
        if(pending.size()>=24)return;
        var ticket=PlannerExecutor.submit(task,snapshot,horizon);
        if(ticket!=null)pending.put(task.slot(),new Work(task,ticket,now,generation));
    }
    /** Debug never schedules or repeats a forecast. */
    public List<GlidePlanner.TracePoint> trace(){
        return debugTrace().points();
    }
    public record DebugTrace(List<GlidePlanner.TracePoint> points,String kind,long age) {}
    public DebugTrace debugTrace(){
        check();for(var c:cache)if(c.generation==generation&&now-c.tick<=8){
            if(c.value instanceof PlanningTask.GlideResult g)return new DebugTrace(g.trace(),"GLIDE_FORECAST",now-c.tick);
            if(c.value instanceof SweepingAttackPlanner.Rollout r)return new DebugTrace(r.trace(),"PATH_FORECAST",now-c.tick);
            if(c.value instanceof SweepingAttackPlanner.Selection s)return new DebugTrace(s.prediction().trace(),"CANDIDATE",now-c.tick);
            if(c.value instanceof SweepingAttackPlanner.Search s&&s.selection()!=null)return new DebugTrace(s.selection().prediction().trace(),"CANDIDATE",now-c.tick);
        }return new DebugTrace(List.of(),"NONE",-1);
    }
    public int pendingCount(){check();return pending.size();}
    public int cellCount(){check();return cells.size();}
    public String detail(){
        check();return "gen="+generation+" cells="+cells.size()+" requested="+requested.size()+" jobs="+pending.size()+" cache="+cache.size()+" "+metrics();
    }
    public long generation(){check();return generation;}
    public static int activeSessions(){return sessions.size();}
    public static String metrics(){return "queue="+PlannerExecutor.queued()+" cache="+cacheHits+" stale="+staleResults+" failed="+failures
        +" captureMs="+captureNanos/1_000_000+" workerMs="+PlannerExecutor.workerNanos.sum()/1_000_000;}
}
