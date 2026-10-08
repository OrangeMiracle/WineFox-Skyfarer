package dev.winefox.skyfarer.flight;

import java.util.*;

public final class RiptideDurationChecks {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        require(RiptidePlanner.maximumTicks(1.5f)==10&&RiptidePlanner.maximumTicks(2.25f)==20&&RiptidePlanner.maximumTicks(3)==30,"level caps");
        var start=new FlightPrediction.State(0,80,0,0,-.1,.7,0,-15);
        var durations=new HashSet<Integer>();
        for(double height:new double[]{80,82,84,86,90,120,160}) {
            var p=RiptidePlanner.choose(start,0,height,120,3,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY);
            require(p!=null,"no variable burst at height "+height);durations.add(p.duration());
            System.out.println("metered height="+height+" duration="+p.duration()+" max="+RiptidePlanner.maximumTicks(p.strength()));
            for(int n=1;n<p.duration();n++)for(double pitch:new double[]{-15,-35,-55}) {
                var shorter=new RiptidePlanner.Plan(0,pitch,height,0,120,3,n,false,0,List.of());
                require(RiptidePlanner.certify(start,shorter,0,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY)==null,"not shortest sufficient burst");
            }
        }
        require(durations.size()>=3&&durations.stream().filter(n->n<30).count()>=3,"III only offers maximum duration");
        var jump=new FlightPrediction.State(0,.82,0,0,.3332,0,0,0);
        var nearAim=new FlightPrediction.Aim(0,-3,10,.3,7.8,0,0,0,0,2.25,.1,10);
        require(LaunchAttackPlanner.choose(jump,List.of(new LaunchAttackPlanner.Target(new UUID(0,1),nearAim)),List.of(),.08,5,.2,8,(a,b,s,v)->true,(a,b,t)->true)==null,"scenario unexpectedly permits uphill Launch Attack");
        int departureDuration=0;
        for(double distance:new double[]{6,10,32,64,256,900}) {
            var p=RiptidePlanner.choose(jump,0,7.3,distance,3,.08,(a,b,s,v)->Math.min(a.y(),b.y())>.55,SurfaceLaunchPlanner.DRY,RiptidePlanner.WINDUP);
            require(p!=null&&p.departure()&&p.duration()<30,"near target wrongly blocks ordinary departure "+distance);
            require(p.trace().getLast().y()>=6.3,"hop incorrectly accepted as departure");
            require(RiptidePlanner.departureReady(p.trace().get(p.duration()),p),"thrust cutoff lacks sustainable energy");
            require(RiptidePlanner.departureReady(p.trace().getLast(),p),"recovery handoff lacks sustainable energy");
            require(p.duration()>5,"old five-tick hop still accepted");
            if(departureDuration>0)require(p.duration()==departureDuration,"departure budget depends on task distance");
            departureDuration=p.duration();
            for(int n=1;n<p.duration();n++)for(double pitch:new double[]{-35,-55,-70}) {
                var shorter=new RiptidePlanner.Plan(0,pitch,7.3,0,distance,3,n,true,0,List.of());
                require(RiptidePlanner.certify(RiptidePlanner.aimLaunch(jump,0,pitch),shorter,RiptidePlanner.WINDUP,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY)==null,"departure not shortest sustainable duration");
            }
        }
        // A shorter burst must rescue the SAME direction/goal when max duration hits a wall or overshoots.
        boolean wallRescued=false,overshootRescued=false;
        for(double distance:new double[]{20,25,30,40,50,120,240})for(double wall:new double[]{12,16,20,25,30,40}) {
            GlidePlanner.Corridor space=(a,b,s,v)->b.z()+s<wall;
            var p=RiptidePlanner.choose(start,0,80,distance,3,.08,space,SurfaceLaunchPlanner.DRY);
            if(p==null)continue;
            var longest=new RiptidePlanner.Plan(p.yaw(),p.pitch(),p.height(),p.goalX(),p.goalZ(),p.strength(),30,false,0,List.of());
            boolean openSafe=RiptidePlanner.certify(start,longest,0,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY)!=null;
            if(openSafe&&RiptidePlanner.certify(start,longest,0,.08,space,SurfaceLaunchPlanner.DRY)==null)wallRescued=true;
            if(!openSafe)overshootRescued=true;
        }
        require(wallRescued&&overshootRescued,"no shorter wall/overshoot rescue");
        require(RiptidePlanner.choose(jump,0,7.3,10,3,.08,(a,b,s,v)->b.y()<1,SurfaceLaunchPlanner.DRY,RiptidePlanner.WINDUP)==null,"roof clearance ignored");
        require(SurfaceLaunchPlanner.chooseCoast(jump,0,7.3,.08,(a,b,s,v)->true,SurfaceLaunchPlanner.DRY)==null,"stationary hop misclassified as sustained unpowered takeoff");
        System.out.println("Metered Riptide: per-tick shortest duration, near-target departure, full recovery, wall/overshoot shortening and roof rejection passed");
    }
}
