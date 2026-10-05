package dev.winefox.skyfarer.flight;

import java.util.List;

public final class SweepingAttackChecks {
    static GlidePlanner.Corridor space(List<FlightCorridor.Box> boxes){return (a,b,s,v)->{
        if(Math.abs(b.x())>96||Math.abs(b.z())>96)return false;
        for(var box:boxes)if(FlightCorridor.intersects(box,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,s,v))return false;
        return true;
    };}
    static boolean ray(List<FlightCorridor.Box> boxes,FlightPrediction.State s,FlightPrediction.Aim aim,int tick){
        for(var b:boxes)if(FlightCorridor.intersects(b,s.x(),s.y(),s.z(),aim.x()+aim.vx()*tick,aim.bottom()+aim.height()/2+aim.vy()*tick,aim.z()+aim.vz()*tick,0,0,0,0,0))return false;
        return true;
    }
    public static void main(String[] args){
        var entry=new SweepingAttackPlanner.Cubic(new SweepingAttackPlanner.Point(-10,5,0),new SweepingAttackPlanner.Point(-10,5,20),new SweepingAttackPlanner.Point(4,5,0),new SweepingAttackPlanner.Point(0,5,0));
        var loop=new SweepingAttackPlanner.Path(entry,new SweepingAttackPlanner.Cubic(entry.d(),new SweepingAttackPlanner.Point(-4,5,0),new SweepingAttackPlanner.Point(-6,6,0),new SweepingAttackPlanner.Point(-8,8,0)),"LEFT_SWEEP",1.5);
        if(loop.passedContact(entry.a(),4.25))throw new AssertionError("Turning approach aborted before contact because entry crosses tangent plane");
        if(!loop.passedContact(new SweepingAttackPlanner.Point(-4.5,5,0),4.25))throw new AssertionError("Actual pass beyond curve contact not detected");
        var floor=new FlightCorridor.Box(-96,-10,-96,96,0,96);
        var hill=new FlightCorridor.Box(-40,0,-40,0,28,40);
        var shoulder=new FlightCorridor.Box(-10,0,-16,8,24,-8);
        var boxes=List.of(floor,hill,shoulder,new FlightCorridor.Box(0,0,9.5,1.5,12,10.5));
        var aim=new FlightPrediction.Aim(1,9,10,.3,7.8,0,0,0,0,2.25,.1,10);
        var target=new SweepingAttackPlanner.Point(1,13.4,10);
        var chosen=new FlightPrediction.State(9,14,-28,Math.sin(Math.toRadians(35))*1.3,-.05,Math.cos(Math.toRadians(35))*1.3,-35,0);
        var selected=SweepingAttackPlanner.choose(chosen,target,aim,0,.08,5,.2,8,space(boxes),(a,b,t)->ray(boxes,a,b,t));
        if(selected==null||ray(boxes,chosen,aim,0))throw new AssertionError("No attack around an occluding hill shoulder");
        var path=selected.path();
        if(path.entry.d().subtract(path.exit.a()).length()>1e-9||path.entry.d().subtract(path.entry.c()).subtract(path.exit.b().subtract(path.exit.a())).length()>1e-9)throw new AssertionError("C1 join broken");
        if(SweepingAttackPlanner.choose(chosen,target,aim,0,.08,5,.2,8,space(boxes),(a,b,t)->false)!=null)throw new AssertionError("Blocked spear contact accepted");
        if(SweepingAttackPlanner.choose(chosen,target,aim,0,.08,5,.2,8,(a,b,s,v)->false,(a,b,t)->true)!=null)throw new AssertionError("Unloaded/body-blocked curve accepted");
        // High/level/low starts must all generate C1 paths with the actual three-dimensional momentum tangent.
        for(double y:new double[]{8,14,22}){
            var start=new FlightPrediction.State(9,y,-28,.7,y<14?.2:-.2,1,-35,0);
            var velocity=new SweepingAttackPlanner.Point(start.vx(),start.vy(),start.vz()).unit();
            var candidates=SweepingAttackPlanner.candidates(start,target,8);
            for(String style:List.of("DIRECT","LEFT_ARC","RIGHT_ARC","LEFT_SWEEP","RIGHT_SWEEP"))if(candidates.stream().noneMatch(p->p.style.equals(style)))throw new AssertionError("Missing candidate family "+style);
            for(var p:candidates)if(p.entry.b().subtract(p.entry.a()).unit().subtract(velocity).length()>1e-8
                ||p.entry.d().subtract(p.entry.c()).subtract(p.exit.b().subtract(p.exit.a())).length()>1e-8)throw new AssertionError("Discontinuous height variant");
        }
        System.out.println("Continuous cubic attack, C1 contact/exit, occluded approach and final contact/body rejection passed.");
    }
}
