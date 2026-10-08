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
        require(!PlanningTask.request(task().request(),task().request().withPulse(1)),"expired rocket plan reused for a powered request");
        require(!PlanningTask.rocket(1,0,4)&&PlanningTask.rocket(5,7,4),"rocket expiry boundary lost");
        var alternatives=SweepingAttackPlanner.candidates(start(),new SweepingAttackPlanner.Point(0,16,28),8);
        var joinA=new PlanningTask.Rejoin(start(),alternatives.get(0),null,0,.08,5,.3,List.of(1));
        var joinB=new PlanningTask.Rejoin(start(),alternatives.get(1),null,0,.08,5,.3,List.of(1));
        require(!joinA.slot().equals(joinB.slot()),"coast rejoin cancels alternative funded certification");
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
        final Object[] level={new Object()}; final PhysicsContext[] physics={PhysicsContext.VANILLA};
        var source=new PlannerSession.Source(){
            public CollisionSnapshot.Cell capture(long key){require(Thread.currentThread()==owner,"world read off main thread");return AIR;}
            public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> ignored,boolean wet,boolean launch){require(Thread.currentThread()==owner,"snapshot created off main thread");return full;}
            public String dimension(){return dimension[0];}
            public Object levelIdentity(){return level[0];}
            public PhysicsContext physics(long tick){require(Thread.currentThread()==owner,"physics read off main");return physics[0];}
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
        first=session.generation();level[0]=new Object();session.begin(3,"exit","target-b",2,1);
        require(session.generation()>first,"same dimension new Level kept old generation");pending(session,task());
        first=session.generation();physics[0]=new PhysicsContext(.04,0);session.begin(4,"exit","target-b",2,1);
        require(session.generation()>first&&session.pendingCount()==0,"changed physics kept old async result");pending(session,task());
        physics[0]=new PhysicsContext(.04,10);session.begin(5,"exit","target-b",2,1);first=session.generation();
        physics[0]=new PhysicsContext(.04,9);session.begin(6,"exit","target-b",2,1);
        require(session.generation()==first,"natural effect countdown cancelled compatible tasks");
        physics[0]=new PhysicsContext(.04,30);session.begin(7,"exit","target-b",2,1);
        require(session.generation()>first,"effect refresh retained old physics proof");
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var wrong=new Thread(()->{try{session.query(task());}catch(Throwable e){error.set(e);}});wrong.start();wrong.join();require(error.get() instanceof IllegalStateException,"off-thread mailbox access accepted");
        session.close();require(session.pendingCount()==0&&session.cellCount()==0,"close retains jobs/world snapshot");
        var maid=new com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid(
            new dev.winefox.skyfarer.replay.ReplayWorld(),new net.minecraft.world.phys.Vec3(0,12,0));
        var facade=new AsyncPlanning(maid);facade.begin(0,"entry","target",0);
        var waiting=facade.certifyAttack(start(),task().request(),.3,(a,b,side,vertical)->true);
        require(!waiting.safe()&&facade.waiting()&&waiting.reason().equals("async_pending"),
            "cold certificate must veto commitment while allowing this tick's steering to continue");
        facade.close();
        movingCertificates(full);
        long rejected=PlannerExecutor.rejected.sum();var jobs=new ArrayList<PlannerExecutor.Ticket<Object>>();
        for(int i=0;i<400;i++){var job=PlannerExecutor.submit(task(),full,10);if(job!=null)jobs.add(job);require(PlannerExecutor.queued()<=64,"unbounded queue");}
        require(PlannerExecutor.rejected.sum()>rejected,"backpressure was not exercised");for(var job:jobs)job.cancel();
        PlannerSession.stop();require(PlannerExecutor.queued()==0&&PlannerSession.activeSessions()==0,"shutdown leaked queue/session");
        var restarted=PlannerExecutor.submit(task(),full,1);await(restarted);require(restarted.outcome.failure()==null,"restart failed");PlannerSession.stop();
        require(PlannerSession.failures==0,"worker failed: "+PlannerSession.lastFailure);
        System.out.println("Async boundary checks passed: immutable DTOs, world-border keys, strict unknown cells, main-only capture, identical math, generation/target/config/dimension invalidation, cancellation, bounded queues and restart.");
    }
    private static void pending(PlannerSession session,PlanningTask task){try{session.query(task);throw new AssertionError("stale result reused");}catch(PlannerSession.Pending expected){}}
    private static void movingCertificates(CollisionSnapshot full){
        var s=new FlightPrediction.State(0,9.4,-32,0,-.08,1.1,0,0);
        var aim=new FlightPrediction.Aim(0,-3,0,.3,7.8,.32,0,0,0,2.25,.1,10);
        var stationary=new FlightPrediction.Aim(0,-3,0,.3,7.8,0,0,0,0,2.25,.1,10);
        require(!PlanningTask.geometryAim(aim,stationary),"stationary geometry suppresses moving intercept search");
        require(SweepingAttackPlanner.interceptCandidates(s,new SweepingAttackPlanner.Point(0,3.4,0),aim,8).size()==648,"intercept family unbounded or missing lead alternatives");
        var choice=SweepingAttackPlanner.choose(s,new SweepingAttackPlanner.Point(0,3.4,0),aim,0,.08,5,.2,8,full.space(new AtomicBoolean()),(a,b,t)->true);
        require(choice!=null,"moving fixture has no physical strike/exit");
        var snapshots=new CollisionSnapshot[]{full};
        var session=new PlannerSession(new PlannerSession.Source(){
            public CollisionSnapshot.Cell capture(long key){return AIR;}
            public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){return snapshots[0];}
        });
        session.begin(1,"ENTRY","target",0,1);
        var proposal=new PlanningTask.RejoinResult(choice,-1);
        var current=FlightPrediction.step(s,0,0,5,6,6,.08,false);
        var task=new PlanningTask.Rejoin(current,choice.path(),aim.after(1),0,.08,5,.2,List.of());
        var checked=session.recertifyRejoin(task,proposal,null);
        require(checked!=null&&checked.selection().prediction().trace().getFirst().state().equals(current),"certificate reused old state instead of actual motion");
        require(session.recertifyRejoin(task,proposal,new CollisionSnapshot.Bounds(0,0,1))==null,"intercept bypassed navigation bound");
        var moved=new FlightPrediction.Aim(60,-3,0,.3,7.8,-.32,0,0,0,2.25,.1,200);
        require(session.recertifyRejoin(new PlanningTask.Rejoin(current,choice.path(),moved,0,.08,5,.2,List.of()),proposal,null)==null,"stale contact or unfinished native windup accepted");
        snapshots[0]=snapshot(Map.of());require(session.recertifyRejoin(task,proposal,null)==null,"missing geometry accepted");
        var blocked=new HashMap<Long,CollisionSnapshot.Cell>();
        var wall=new CollisionSnapshot.Cell(List.of(new FlightCorridor.Box(-96,-96,-96,96,96,96)),List.of(),0,true);
        full.cells().keySet().forEach(k->blocked.put(k,wall));snapshots[0]=snapshot(blocked);
        require(session.recertifyRejoin(task,proposal,null)==null,"new wall accepted through old certificate");
        session.close();System.out.println("Moving certificate: 648 bounded candidates, current-state rejoin, native windup, missing cells, boundary and wall veto passed");
    }
    private static void await(PlannerExecutor.Ticket<?> ticket)throws Exception{long end=System.nanoTime()+5_000_000_000L;while(ticket.outcome==null&&System.nanoTime()<end)Thread.sleep(2);require(ticket.outcome!=null,"worker timeout");if(ticket.outcome.failure()!=null)throw new AssertionError(ticket.outcome.failure());}
}
