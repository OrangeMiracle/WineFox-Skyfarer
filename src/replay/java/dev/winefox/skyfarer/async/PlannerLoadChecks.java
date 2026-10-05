package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.*;

/** Synthetic scheduler load, not a Minecraft TPS benchmark. */
public final class PlannerLoadChecks {
    public static void main(String[] args)throws Exception{
        final Thread owner=Thread.currentThread();int count=24;int[] captures=new int[count];
        var sessions=new ArrayList<PlannerSession>();var tasks=new ArrayList<PlanningTask>();
        for(int i=0;i<count;i++){
            final int index=i;
            sessions.add(new PlannerSession(new PlannerSession.Source(){
                public CollisionSnapshot.Cell capture(long key){
                    if(Thread.currentThread()!=owner)throw new AssertionError("off-thread world access");captures[index]++;
                    return new CollisionSnapshot.Cell(List.of(),List.of(),0,true);
                }
                public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){return new CollisionSnapshot(cells,.6,.6,.4,false,-1024,-1024,1024,1024);}
            }));
            var s=new FlightPrediction.State(i*8,12,0,0,0,1,0,0);
            tasks.add(new PlanningTask.Glide(s,new GlidePlanner.Request(0,12,0,0,14,Double.NaN,0,5,3,8,.08,.25,.25,false,null)));
        }
        long[] timings=new long[100];int ready=0;long reads=PlannerSession.capturedBlocks,worker=PlannerExecutor.workerNanos.sum();
        for(int tick=0;tick<timings.length;tick++){
            long before=PlannerSession.capturedBlocks,begin=System.nanoTime();PlannerSession.serverTick();
            for(int i=0;i<count;i++){
                var session=sessions.get(i);session.begin(tick,"entry",i+"-"+(tick/31),0,1);
                try{session.query(tasks.get(i));ready++;}catch(PlannerSession.Pending expected){}
                if(session.pendingCount()>24||session.cellCount()>768)throw new AssertionError("unbounded per-maid state");
            }
            timings[tick]=System.nanoTime()-begin;
            if(PlannerSession.capturedBlocks-before>21600||PlannerExecutor.queued()>64)throw new AssertionError("global budget exceeded");
            Thread.sleep(10);
        }
        for(int i=0;i<count;i++)if(captures[i]==0)throw new AssertionError("maid starved: "+i);
        if(ready==0||PlannerSession.failures!=0)throw new AssertionError("no useful plans: "+PlannerSession.lastFailure);
        PlannerSession.stop();if(PlannerSession.activeSessions()!=0)throw new AssertionError("sessions retained");Arrays.sort(timings);
        String report=String.format(Locale.ROOT,"Synthetic 24-owner / 100-tick scheduler load (not MC TPS): ready=%d; main p50=%.3f ms, p95=%.3f ms, max=%.3f ms; worker CPU=%.3f ms; captured block reads=%d; every owner served; queue <=64, per-owner jobs <=24 and cells <=768; shutdown clean.%n",
            ready,timings[50]/1e6,timings[95]/1e6,timings[99]/1e6,(PlannerExecutor.workerNanos.sum()-worker)/1e6,PlannerSession.capturedBlocks-reads);
        var dir=java.nio.file.Path.of("build/reports");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("async-planner-load.txt"),report);System.out.print(report);
    }
}
