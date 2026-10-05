package dev.winefox.skyfarer.async;
import dev.winefox.skyfarer.flight.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncSweepChecks {
    public static void main(String[] args)throws Exception{
        var boxes=List.of(new FlightCorridor.Box(-96,-10,-96,96,0,96),new FlightCorridor.Box(-40,0,-40,0,28,40),new FlightCorridor.Box(-10,0,-16,8,24,-8),new FlightCorridor.Box(0,0,9.5,1.5,12,10.5));
        var shape=new CollisionSnapshot.Cell(boxes,List.of(),0,true);
        var map=new HashMap<Long,CollisionSnapshot.Cell>();for(int x=-24;x<=24;x++)for(int y=-4;y<=16;y++)for(int z=-24;z<=24;z++)map.put(CollisionSnapshot.key(x,y,z),shape);
        var snapshot=new CollisionSnapshot(map,.6,.6,.4,false,-96,-96,96,96);
        var aim=new FlightPrediction.Aim(1,9,10,.3,7.8,0,0,0,0,2.25,.1,10);
        var target=new SweepingAttackPlanner.Point(1,13.4,10);
        var initial=new FlightPrediction.State(9,14,-28,Math.sin(Math.toRadians(35))*1.3,-.05,Math.cos(Math.toRadians(35))*1.3,-35,0);
        var task=new PlanningTask.Sweep(initial,target,aim,0,.08,5,.2,8,0,null,false,120);
        var selected=(SweepingAttackPlanner.Selection)task.compute(snapshot.space(new AtomicBoolean()));
        if(selected==null)throw new AssertionError("Full immutable snapshot changed hillside geometry");
        System.out.println("Snapshot curve="+selected.path().style+" contact="+selected.prediction().contactTick());
        var source=new PlannerSession.Source(){
            public CollisionSnapshot.Cell capture(long key){return shape;}
            public CollisionSnapshot snapshot(Map<Long,CollisionSnapshot.Cell> cells,boolean wet,boolean launch){return new CollisionSnapshot(cells,.6,.6,.4,false,-96,-96,96,96);}
        };
        var session=new PlannerSession(source);var s=initial;boolean found=false;
        for(int tick=0;tick<12;tick++){
            PlannerSession.serverTick();session.begin(tick,"entry","target",0,1);
            try{var choice=(SweepingAttackPlanner.Selection)session.query(new PlanningTask.Sweep(s,target,aim,0,.08,5,.2,8,0,null,false,120));System.out.println("tick "+tick+" ready="+(choice!=null)+" "+session.detail());if(choice!=null){var review=SweepingAttackPlanner.simulate(s,choice.path(),aim,0,.08,5,.2,false,snapshot.space(new AtomicBoolean()),(a,b,t)->snapshot.space(new AtomicBoolean()).ray(a.x(),a.y(),a.z(),b.x(),b.bottom()+b.height()/2,b.z()),120);System.out.println("review="+review.safe()+" hit="+review.hit()+" contact="+review.contactTick()+" last="+review.trace().getLast().state());found=true;break;}}
            catch(PlannerSession.Pending pending){System.out.println("tick "+tick+" pending "+session.detail());}
            s=FlightPrediction.step(s,s.yaw(),0,5,3,8,.08,false);Thread.sleep(50);
        }
        PlannerSession.stop();if(!found)throw new AssertionError("Cold moving-entity sweep never ready");
    }
}
