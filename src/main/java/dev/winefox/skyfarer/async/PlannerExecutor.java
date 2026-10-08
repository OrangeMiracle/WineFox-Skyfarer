package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.PredictionHorizon;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Shared bounded executor. Never runs rejected work on the submitting/server thread. */
public final class PlannerExecutor {
    private PlannerExecutor(){}
    private static ThreadPoolExecutor pool,geometryPool;
    private static final AtomicInteger threadIds=new AtomicInteger();
    public static final LongAdder submitted=new LongAdder(),completed=new LongAdder(),rejected=new LongAdder(),cancelled=new LongAdder(),workerNanos=new LongAdder();
    public sealed interface Task<T> permits PlanningTask { T compute(CollisionSnapshot.Space space); }
    public record Outcome<T>(T value,Set<Long> missing,Set<Long> preferred,Throwable failure){}
    public static final class Ticket<T> implements Runnable {
        private final Task<T> task;private final CollisionSnapshot snapshot;private final double horizon;
        public final AtomicBoolean cancellation=new AtomicBoolean();
        public volatile Outcome<T> outcome;
        private Ticket(Task<T> task,CollisionSnapshot snapshot,double horizon){this.task=task;this.snapshot=snapshot;this.horizon=horizon;}
        @Override public void run(){
            if(cancellation.get())return;long begin=System.nanoTime();
            PredictionHorizon.enterSnapshot(horizon);
            try{
                var space=snapshot.space(cancellation);T value=task.compute(space);
                var missing=space.missing();Set<Long> preferred=Set.of();
                if(!missing.isEmpty()){
                    // Discovery only: look through absent snapshot cells to request the whole route.
                    // Its result is NEVER published or used to certify a collision corridor.
                    var discovery=snapshot.space(cancellation);discovery.explore();Object speculative=task.compute(discovery);
                    // Search wraps Selection now. Preserve the selected corridor's priority;
                    // otherwise unrelated candidate cells can starve its current-state rejoin.
                    if(speculative instanceof dev.winefox.skyfarer.flight.SweepingAttackPlanner.Search search)speculative=search.selection();
                    if(speculative instanceof PlanningTask.RejoinResult rejoin)speculative=rejoin.selection();
                    if(speculative instanceof dev.winefox.skyfarer.flight.SweepingAttackPlanner.Selection selected){
                        var proof=snapshot.space(cancellation);var points=selected.path().points();
                        for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);proof.clear(CollisionSnapshot.point(a.x(),a.y(),a.z()),CollisionSnapshot.point(b.x(),b.y(),b.z()),.15,.12);}
                        var trace=selected.prediction().trace();for(int i=1;i<trace.size();i++)proof.clear(trace.get(i-1).state(),trace.get(i).state(),.15,.12);
                        preferred=proof.missing();
                    }
                    else if(speculative instanceof PlanningTask.GlideResult glide){
                        var proof=snapshot.space(cancellation);var trace=glide.trace();
                        for(int i=1;i<trace.size();i++)proof.clear(trace.get(i-1).state(),trace.get(i).state(),.15,.12);
                        preferred=proof.missing();
                    }
                    else if(speculative instanceof dev.winefox.skyfarer.flight.EmergencyAvoidancePlanner.Plan
                         || speculative instanceof dev.winefox.skyfarer.flight.LaunchAttackPlanner.Plan){
                        var trace=speculative instanceof dev.winefox.skyfarer.flight.EmergencyAvoidancePlanner.Plan e?e.trace()
                            :((dev.winefox.skyfarer.flight.LaunchAttackPlanner.Plan)speculative).selection().prediction().trace();
                        var proof=snapshot.space(cancellation);
                        for(int i=1;i<trace.size();i++)proof.clear(trace.get(i-1).state(),trace.get(i).state(),.15,.12);
                        preferred=proof.missing();
                    }
                    else if(speculative instanceof dev.winefox.skyfarer.flight.SweepingAttackPlanner.Rollout rollout){
                        var proof=snapshot.space(cancellation);var trace=rollout.trace();
                        for(int i=1;i<trace.size();i++)proof.clear(trace.get(i-1).state(),trace.get(i).state(),.15,.12);
                        preferred=proof.missing();
                    }
                    var union=new java.util.LinkedHashSet<>(missing);union.addAll(discovery.missing());missing=Set.copyOf(union);
                }
                if(!cancellation.get())outcome=new Outcome<>(value,missing,preferred,null);
            }
            catch(CancellationException ignored){}
            catch(RuntimeException failure){outcome=new Outcome<>(null,Set.of(),Set.of(),failure);}
            finally{PredictionHorizon.leaveSnapshot();workerNanos.add(System.nanoTime()-begin);completed.increment();}
        }
        public void cancel(){if(cancellation.compareAndSet(false,true)){cancelled.increment();synchronized(PlannerExecutor.class){if(pool!=null)pool.remove(this);if(geometryPool!=null)geometryPool.remove(this);}}}
    }
    public static synchronized <T> Ticket<T> submit(Task<T> task,CollisionSnapshot snapshot,double horizon){
        if(pool==null||pool.isShutdown()){
            int threads=Math.max(2,Math.min(4,Runtime.getRuntime().availableProcessors()-2));
            pool=create(threads-1);geometryPool=create(1);
        }
        var ticket=new Ticket<T>(task,snapshot,horizon);
        var dto=((PlanningTask)task).unbound();
        boolean geometry=dto instanceof PlanningTask.Slope||dto instanceof PlanningTask.Lane||dto instanceof PlanningTask.LaunchAttack||dto instanceof PlanningTask.Sweep s&&s.path()==null;
        try{(geometry?geometryPool:pool).execute(ticket);submitted.increment();return ticket;}catch(RejectedExecutionException full){rejected.increment();return null;}
    }
    private static ThreadPoolExecutor create(int threads){
        var result=new ThreadPoolExecutor(threads,threads,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"WFS-Planner-"+threadIds.incrementAndGet());t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        result.allowCoreThreadTimeOut(true);return result;
    }
    public static synchronized int queued(){return pool==null?0:pool.getQueue().size()+geometryPool.getQueue().size();}
    public static synchronized void shutdown(){if(pool!=null){
        for(var executor:new ThreadPoolExecutor[]{pool,geometryPool})for(var r:executor.shutdownNow())if(r instanceof Ticket<?> ticket)ticket.cancel();
        pool=null;geometryPool=null;
    }}
}
