package dev.winefox.skyfarer.flight;

import java.util.List;

/** Replays real body sweeps and all random rocket lifetimes, without launching Minecraft. */
public final class SurfaceLaunchChecks {
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
    private static GlidePlanner.Corridor space(List<FlightCorridor.Box> boxes) {
        return (a,b,side,vertical)->boxes.stream().noneMatch(box->FlightCorridor.intersects(box,
            a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,side,vertical));
    }
    public static void main(String[] args) {
        int cases=0;
        for(double snow:new double[]{0,.125,.25,.5,.875,1}) for(double inertia:new double[]{0,.3,.65}) for(int duration:new int[]{0,1,2}) {
            var world=space(List.of(new FlightCorridor.Box(-200,-10,-200,200,snow,200)));
            var start=new FlightPrediction.State(0,snow+.82,0,inertia,.33,inertia*.4,0,0);
            var plan=SurfaceLaunchPlanner.choose(start,0,start.y()+6,.08,duration,world,SurfaceLaunchPlanner.DRY);
            require(plan!=null,"ice/snow departure missing snow="+snow+" inertia="+inertia);
            replay(start,plan,duration,world,SurfaceLaunchPlanner.DRY,true); cases++;
        }
        for(double depth:new double[]{1,4,8}) for(int duration:new int[]{1,2}) {
            var world=space(List.of(new FlightCorridor.Box(-200,-10,-200,200,0,200)));
            var start=new FlightPrediction.State(0,.5,0,0,.04,0,0,0);
            SurfaceLaunchPlanner.Water water=s->s.y()-.35<depth;
            var plan=SurfaceLaunchPlanner.choose(start,0,depth+4,.08,duration,world,water);
            require(plan!=null,"water recovery missing depth="+depth+" duration="+duration);
            replay(start,plan,duration,world,water,true); cases++;
        }
        var floor=new FlightCorridor.Box(-200,-10,-200,200,0,200);
        var roof=new FlightCorridor.Box(-5,2,-5,5,3,5);
        var wall=new FlightCorridor.Box(-5,0,3,5,5,4);
        var room=space(List.of(floor,roof,wall));
        var jump=new FlightPrediction.State(0,.82,0,0,.33,0,0,0);
        var side=SurfaceLaunchPlanner.choose(jump,0,6.82,.08,1,room,SurfaceLaunchPlanner.DRY);
        require(side!=null,"low-roof side launch must remain possible");
        replay(jump,side,1,room,SurfaceLaunchPlanner.DRY,false);
        var sealed=space(List.of(floor,new FlightCorridor.Box(-200,2,-200,200,3,200)));
        SurfaceLaunchPlanner.Water submerged=s->s.y()-.35<3;
        require(SurfaceLaunchPlanner.choose(new FlightPrediction.State(0,.5,0,0,.04,0,0,0),0,7,.08,1,sealed,submerged)==null,
            "roofed water must refuse an impossible pulse and allow swim fallback");
        var waterStep=SurfaceLaunchPlanner.step(new FlightPrediction.State(1,1,1,.2,.1,.3,0,0),0,-88,.08,true,true);
        require(Math.abs(waterStep.x()-1.2)<1e-9 && Math.abs(waterStep.vx()-.09)<1e-9,"swimming move/drag before rocket order");
        System.out.println(cases+" ice/snow/water departures across every rocket lifetime, low-roof side launch and sealed-water fallback passed; no game launched.");
    }
    private static void replay(FlightPrediction.State start,SurfaceLaunchPlanner.Plan plan,int duration,
                               GlidePlanner.Corridor corridor,SurfaceLaunchPlanner.Water water,boolean rise) {
        require(Double.isFinite(SurfaceLaunchPlanner.pulseCost(start,plan,.08,duration,corridor,water)),"chosen pulse must validate this exact attitude");
        for(int life=RocketProfile.minimumTicks(duration);life<=RocketProfile.maximumTicks(duration);life++) {
            var s=start;
            for(int tick=0;tick<life+16;tick++) {
                double pitch=SurfaceLaunchPlanner.command(s,plan,tick,.08,tick<life);
                var next=SurfaceLaunchPlanner.step(s,plan.yaw(),pitch,.08,tick<life,water.wet(s));
                require(corridor.clear(s,next,0,0),"actual body collision at tick="+tick+" "+plan);
                s=next;
                if(rise && tick==5) require(s.y()-start.y()>1,"must climb promptly instead of orbiting surface");
            }
            require(!water.wet(s),"must finish above water after the pulse and coast");
        }
    }
}
