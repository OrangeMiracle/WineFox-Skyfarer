package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests the production mailbox/executor, not an inline replacement executor. */
public final class AsyncPlannerChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final CollisionSnapshot.Cell AIR=new CollisionSnapshot.Cell(List.of(),List.of(),0,true);
    private static CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells){return new CollisionSnapshot(cells,.6,.6,.4,false,-30_000_000,-30_000_000,30_000_000,30_000_000);}
    private static FlightPrediction.State start(){return new FlightPrediction.State(0,12,0,0,0,1,0,0);}
    private static PlanningTask.Glide task(){return new PlanningTask.Glide(start(),new GlidePlanner.Request(0,12,0,0,14,Double.NaN,0,5,3,8,.08,.25,.25,false,null));}
    public static void main(String[] args)throws Exception{
        for(int x:new int[]{-7_500_000,-1,0,1,7_500_000})for(int y:new int[]{-16,0,80})for(int z:new int[]{-7_500_000,-1,0,1,7_500_000}){
            long key=CollisionSnapshot.key(x,y,z);require(CollisionSnapshot.x(key)==x&&CollisionSnapshot.y(key)==y&&CollisionSnapshot.z(key)==z,"cell key aliases coordinates");
        }
        var mutable=new HashMap<Long,CollisionSnapshot.Cell>();mutable.put(CollisionSnapshot.key(0,3,0),AIR);
        var frozen=snapshot(mutable);mutable.clear();require(frozen.cells().size()==1,"mutable map leaked");
        try{frozen.cells().clear();throw new AssertionError("snapshot map mutable");}catch(UnsupportedOperationException expected){}
        var unknown=snapshot(Map.of()).space(new AtomicBoolean());require(!unknown.clear(start(),start(),0,0),"unknown certified as air");
        var cells=new HashMap<Long,CollisionSnapshot.Cell>();
        for(int x=-18;x<=18;x++)for(int y=-4;y<=16;y++)for(int z=-18;z<=18;z++)cells.put(CollisionSnapshot.key(x,y,z),AIR);
        var full=snapshot(cells);var space=full.space(new AtomicBoolean());
        space.navigation(new CollisionSnapshot.Bounds(0,0,5));require(!space.clear(start(),CollisionSnapshot.point(0,12,8),0,0),"navigation bound lost");
        var cancellation=new AtomicBoolean(true);try{full.space(cancellation).clear(start(),start(),0,0);throw new AssertionError("cancellation ignored");}catch(java.util.concurrent.CancellationException expected){}
        var ticket=PlannerExecutor.submit(task(),full,1);require(ticket!=null,"first job rejected");await(ticket);
        var actual=(PlanningTask.GlideResult)ticket.outcome.value();var expected=GlidePlanner.choose(start(),task().request(),full.space(new AtomicBoolean()));
        require(actual.plan().equals(expected),"worker changed planner math");
        require(PlannerExecutor.Task.class.isSealed(),"open callback execution boundary");
        PredictionHorizon.multiplier=()->{throw new AssertionError("worker read live config");};
        var frozenConfig=PlannerExecutor.submit(task(),full,1);await(frozenConfig);PredictionHorizon.multiplier=()->1;
        var origin=new PlanningTask.Compact(start(),new CompactTurnPlanner.Goal(20,18,20,0,0,48),0,.08,5,1);
        var advanced=new PlanningTask.Compact(start(),new CompactTurnPlanner.Goal(20,18,20,.3,.2,48.4),0,.08,5,1);
        var teleported=new PlanningTask.Compact(start(),new CompactTurnPlanner.Goal(20,18,20,30,0,48),0,.08,5,1);
        require(advanced.compatible(origin)&&!teleported.compatible(origin),"moving recovery origin starves safe pulse results or accepts teleport");
        require(PlannerSession.contactStateCompatible(start(),start(),1),"fresh contact certificate rejected");
        require(!PlannerSession.contactStateCompatible(new FlightPrediction.State(0,12,1,0,0,1,15,0),start(),1),"turned-away contact certificate accepted");
        require(!PlannerSession.contactStateCompatible(new FlightPrediction.State(0,12,1,0,0,1,0,-20),start(),1),"changed pitch contact certificate accepted");
        require(!PlannerSession.contactStateCompatible(start(),start(),3),"old contact certificate accepted");
        require(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().startsWith("WFS-Planner-")),"no actual worker thread");
        var cold=PlannerExecutor.submit(task(),snapshot(Map.of()),1);await(cold);
        require(!cold.outcome.missing().isEmpty()&&!((PlanningTask.GlideResult)cold.outcome.value()).plan().safe(),"exploration escaped certification boundary");
        final Thread owner=Thread.currentThread();final String[] dimension={"a"};
        var source=new PlannerSession.Source(){
            public CollisionSnapshot.Cell capture(long key){require(Thread.currentThread()==owner,"world read off main thread");return AIR;}
            public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> ignored,boolean wet,boolean launch){require(Thread.currentThread()==owner,"snapshot created off main thread");return full;}
            public String dimension(){return dimension[0];}
        };
        var session=new PlannerSession(source);session.begin(0,"attack","target-a",1,1);
        pending(session,task());require(session.pendingCount()==1,"job not queued");
        long first=session.generation();session.begin(1,"exit","target-b",1,1);
        require(session.generation()>first&&session.pendingCount()==0,"old phase/target job retained");pending(session,task());
        long deadline=System.nanoTime()+3_000_000_000L;Object result=null;
        while(result==null&&System.nanoTime()<deadline){Thread.sleep(5);try{result=session.query(task());}catch(PlannerSession.Pending ignored){}}
        require(result!=null,"main mailbox never receives result");
        first=session.generation();dimension[0]="b";session.begin(2,"exit","target-b",1,1);require(session.generation()>first,"dimension not invalidated");pending(session,task());
        first=session.generation();session.begin(3,"exit","target-b",2,1);require(session.generation()>first,"config not invalidated");
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var wrong=new Thread(()->{try{session.query(task());}catch(Throwable e){error.set(e);}});wrong.start();wrong.join();require(error.get() instanceof IllegalStateException,"off-thread mailbox access accepted");
        session.close();require(session.pendingCount()==0&&session.cellCount()==0,"close retains jobs/world snapshot");
        long rejected=PlannerExecutor.rejected.sum();var jobs=new ArrayList<PlannerExecutor.Ticket<Object>>();
        for(int i=0;i<400;i++){var job=PlannerExecutor.submit(task(),full,10);if(job!=null)jobs.add(job);require(PlannerExecutor.queued()<=64,"unbounded queue");}
        require(PlannerExecutor.rejected.sum()>rejected,"backpressure was not exercised");for(var job:jobs)job.cancel();
        PlannerSession.stop();require(PlannerExecutor.queued()==0&&PlannerSession.activeSessions()==0,"shutdown leaked queue/session");
        var restarted=PlannerExecutor.submit(task(),full,1);await(restarted);require(restarted.outcome.failure()==null,"restart failed");PlannerSession.stop();
        require(PlannerSession.failures==0,"worker failed: "+PlannerSession.lastFailure);
        System.out.println("Async boundary checks passed: immutable DTOs, world-border keys, strict unknown cells, main-only capture, identical math, generation/target/config/dimension invalidation, cancellation, bounded queues and restart.");
    }
    private static void pending(PlannerSession session,PlanningTask task){try{session.query(task);throw new AssertionError("stale result reused");}catch(PlannerSession.Pending expected){}}
    private static void await(PlannerExecutor.Ticket<?> ticket)throws Exception{long end=System.nanoTime()+5_000_000_000L;while(ticket.outcome==null&&System.nanoTime()<end)Thread.sleep(2);require(ticket.outcome!=null,"worker timeout");if(ticket.outcome.failure()!=null)throw new AssertionError(ticket.outcome.failure());}
}
