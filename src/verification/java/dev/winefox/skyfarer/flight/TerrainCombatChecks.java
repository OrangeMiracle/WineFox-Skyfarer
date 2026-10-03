package dev.winefox.skyfarer.flight;

import dev.winefox.skyfarer.ai.TargetPriority;
import dev.winefox.skyfarer.ai.AttackPolicy;
import java.util.ArrayList;
import java.util.List;

/** Signed attack, launch and tactical policy checks without a game instance. */
public final class TerrainCombatChecks {
    private static GlidePlanner.Corridor world(List<FlightCorridor.Box> boxes) {
        return (a,b,side,vertical)->boxes.stream().noneMatch(box->FlightCorridor.intersects(box,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.3,.6,.4,side,vertical));
    }
    private static FlightPrediction.State point(double x,double y,double z) { return new FlightPrediction.State(x,y,z,0,0,0,0,0); }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        var nearby=TargetPriority.rank(false,10,16,false,-100);
        var farPhantom=TargetPriority.rank(false,80,16,true,1000);
        require(nearby.compareTo(farPhantom)>0,"strategic creeper must beat distant phantom");
        require(TargetPriority.rank(false,12,16,true,-1000).compareTo(nearby)>0,"phantom wins within same layer");
        require(TargetPriority.rank(true,40,16,false,10).sameLayer(nearby),"previous and nearby targets share a layer");
        require(TargetPriority.protectedRadius(true,32)==48 && TargetPriority.protectedRadius(false,32)==16,"Home radius follows actual restriction plus 16");
        require(AttackPolicy.canResume("NO_TARGET_AFTER_LOITER",true,true,true,true,true),"soft return resumes in air");
        for(String reason:new String[]{"TASK_CHANGED","SITTING","FLIGHT_LIMIT_OR_FUEL","GLIDE_INTERRUPTED"})
            require(!AttackPolicy.canResume(reason,true,true,true,true,true),"hard return must remain authoritative");
        require(!AttackPolicy.canResume("NO_TARGET_AFTER_LOITER",true,true,false,true,true),"no fuel means no reentry");
        require(FlightPrediction.pathHeight(0,1.3,-.3,-1)==1.3 && FlightPrediction.pathHeight(-4,1.3,-.3,-1)>1.3,"uphill pass must keep climbing through contact");
        int cases=0;
        for(double grade:new double[]{-.5,-.25,.25,.5}) for(double speed:new double[]{.8,1.2,1.7}) for(int pulse:new int[]{0,26,52}) {
            slope(grade,speed,pulse); cases++;
        }
        launch();
        int steep=0;
        for(double grade:new double[]{-.75,-1,-1.25,.75,1,1.25}) for(double speed:new double[]{.45,.8,1.2}) for(int pulse:new int[]{0,26}) {
            precisionSlope(grade,speed,pulse); steep++;
        }
        precisionSafety();
        System.out.println(steep+" steep/slow attack replays with finite pulses and planned exits passed.");
        System.out.println(cases+" sloped attack replays, low-roof launch, target layers and airborne reengagement checks passed; no game launched.");
    }
    private static void precisionSlope(double grade,double speed,int rocket) {
        var boxes=new ArrayList<FlightCorridor.Box>();
        for(int z=-100;z<120;z++) boxes.add(new FlightCorridor.Box(-15,-200,z,15,Math.floor(grade*(z+.5)),z+1));
        var space=world(boxes);
        var s=new FlightPrediction.State(0,1.5-grade*32,-32,0,grade*speed,speed,0,-Math.toDegrees(Math.atan(grade)));
        var line=AttackLinePlanner.choose(s,0,1.5,0,0,1,grade,AttackPolicy.minimumEntry(rocket>0?1.7:speed,10,2.25),12,3,true,
            (ax,ay,az,bx,by,bz)->space.clear(point(ax,ay,az),point(bx,by,bz),.35,.35));
        require(line!=null,"steep line missing grade="+grade);
        // Replay begins after the controller's merge/stability gate has reached this chosen line.
        s=new FlightPrediction.State(line.entryX(),line.entryY(),line.entryZ(),0,line.gradient()*speed,speed,0,
            -Math.toDegrees(Math.atan(line.gradient())));
        int charge=-1,nextBoost=rocket>0?30:0; boolean hit=false;
        for(int tick=0;tick<150 && s.z()<=2.25;tick++) {
            var aim=new FlightPrediction.Aim(0,-3,0,.3,7.8,0,0,0,1,2.25,.125,charge<0?10:Math.max(0,10-tick+charge));
            var r=new GlidePlanner.Request(0,line.height(),-s.z(),-line.gradient(),-1,Double.NaN,Math.max(0,rocket-tick),2.5,6,10,.08,.35,.35,false,s.z()<0?aim:null)
                .withExit(new GlidePlanner.Exit(line.exitX(),line.exitY(),line.exitZ()));
            var p=GlidePlanner.choose(s,r,space);
            if(tick>=rocket && tick>=nextBoost) {
                var coast=GlidePlanner.forecast(s,r,p,space,.4);
                if(!GlidePlanner.ordinary(p)||!coast.safe()||coast.reserve()<.02) {
                    int selected=-1; double cost=Double.POSITIVE_INFINITY;
                    for(int d:new int[]{1,2,3}) {double c=GlidePlanner.pulseCost(s,r,space,d,.4,.02); if(c<cost) {cost=c;selected=d;} }
                    if(selected>=0) {rocket=tick+(int)RocketProfile.expectedTicks(selected);nextBoost=tick+30;r=r.withPulse(rocket-tick);p=GlidePlanner.choose(s,r,space);}
                }
            }
            require(GlidePlanner.ordinary(p),"steep abandoned grade="+grade+" speed="+speed+" tick="+tick+" state="+s+" plan="+p);
            double pitch=FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate());
            var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),0,pitch);
            if(charge>=0 && tick-charge>=10 && actualContact(aimed,boxes)) hit=true;
            double currentSpeed=Math.max(rocket>tick?1.7:.25,Math.hypot(s.vx(),s.vz()));
            if(charge<0 && FlightMath.chargeDue(-s.z(),currentSpeed,10)) charge=tick;
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,tick<rocket);
            require(space.clear(s,next,0,0),"steep body collision");s=next;
        }
        require(hit,"steep miss grade="+grade+" speed="+speed+" end="+s+" line="+line);
        for(int tick=0;tick<12;tick++) {
            double yaw=Math.toDegrees(Math.atan2(s.x()-line.exitX(),line.exitZ()-s.z()));
            var r=new GlidePlanner.Request(yaw,line.exitY(),0,0,14,Double.NaN,Math.max(0,rocket-150),5,6,10,.08,.35,.35,true,null)
                .withExit(new GlidePlanner.Exit(line.exitX(),line.exitY(),line.exitZ(),0,grade));
            var p=GlidePlanner.choose(s,r,space);
            require(GlidePlanner.ordinary(p),"steep exit abandoned grade="+grade+" "+p);
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,false);
            require(space.clear(s,next,0,0),"steep exit collision");s=next;
            if(Math.hypot(s.x()-line.exitX(),s.z()-line.exitZ())<3) break;
        }
    }
    private static void precisionSafety() {
        require(GlidePlanner.wallDamage(.25,0)==0,"slow contact causes no vanilla fly-into-wall damage");
        require(GlidePlanner.wallDamage(1.7,.3)>10,"large horizontal speed loss is dangerous");
        require(AttackLinePlanner.gradient(-4,0,4,3)>1.3,"steep support gradient must not clamp at .8");
        var wall=new FlightCorridor.Box(-100,-2,4.5,100,20,5.5);
        var space=world(List.of(wall));
        var s=new FlightPrediction.State(0,3,0,0,0,.45,0,0);
        var r=new GlidePlanner.Request(0,3,0,0,14,Double.NaN,0,2.5,3,4,.08,.35,.35,false,null);
        var p=GlidePlanner.choose(s,r,space);
        require(GlidePlanner.ordinary(p),"slow near-wall flight should find bounded correction before emergency: "+p);
        require(p.pitchRate()<=12 && p.yawRate()<=12,"low-risk correction must not snap 90/180 degrees");
    }
    private static void slope(double grade,double speed,int rocket) {
        var boxes=new ArrayList<FlightCorridor.Box>();
        for(int z=-100;z<120;z++) boxes.add(new FlightCorridor.Box(-15,-100,z,15,Math.floor(grade*(z+.5)),z+1));
        var space=world(boxes);
        var s=new FlightPrediction.State(0,1.5-grade*40,-40,0,grade*speed,speed,0,-Math.toDegrees(Math.atan(grade)));
        var line=AttackLinePlanner.choose(s,0,1.5,0,0,1,grade,22,12,3,true,
            (ax,ay,az,bx,by,bz)->space.clear(point(ax,ay,az),point(bx,by,bz),.35,.35));
        require(line!=null,"slope line missing grade="+grade);
        int charge=-1,nextBoost=rocket>0?30:0; boolean hit=false;
        for(int tick=0;tick<130 && s.z()<4;tick++) {
            var aim=new FlightPrediction.Aim(0,-3,0,.3,7.8,0,0,0,1,2.25,.125,charge<0?10:Math.max(0,10-tick+charge));
            var r=new GlidePlanner.Request(0,line.height(),-s.z(),-line.gradient(),-1,Double.NaN,Math.max(0,rocket-tick),2.5,3,4,.08,.35,.35,false,aim);
            var p=GlidePlanner.choose(s,r,space);
            if(tick>=rocket && tick>=nextBoost) {
                var coast=GlidePlanner.forecast(s,r,p,space,.8);
                if(!GlidePlanner.ordinary(p)||!coast.safe()||coast.reserve()<.08) {
                    int selected=-1; double cost=Double.POSITIVE_INFINITY;
                    for(int d:new int[]{1,2,3}) { double c=GlidePlanner.pulseCost(s,r,space,d,.8,.08); if(c<cost) { cost=c; selected=d; } }
                    if(selected>=0) { rocket=tick+(int)RocketProfile.expectedTicks(selected); nextBoost=tick+30; r=r.withPulse(rocket-tick); p=GlidePlanner.choose(s,r,space); }
                }
            }
            require(GlidePlanner.ordinary(p),"slope abandoned grade="+grade+" speed="+speed+" tick="+tick+" state="+s+" plan="+p);
            double pitch=FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate());
            var aimed=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),0,pitch);
            if(charge>=0 && tick-charge>=10 && actualContact(aimed,boxes)) hit=true;
            if(charge<0 && FlightMath.chargeDue(-s.z(),Math.max(1.7,Math.hypot(s.vx(),s.vz())),10)) charge=tick;
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,tick<rocket);
            require(space.clear(s,next,0,0),"slope collision grade="+grade); s=next;
        }
        require(hit,"slope miss grade="+grade+" speed="+speed+" end="+s+" line="+line);
    }
    private static boolean actualContact(FlightPrediction.State s,List<FlightCorridor.Box> boxes) {
        double angle=Math.toRadians(s.pitch()),dy=-Math.sin(angle),dz=Math.cos(angle);
        double far=2.25+Math.max(0,s.vy()*dy+s.vz()*dz);
        for(double t=0;t<=far;t+=.02) {
            double y=s.y()+dy*t,z=s.z()+dz*t;
            for(var box:boxes) if(y>box.minY() && y<box.maxY() && z>box.minZ() && z<box.maxZ()) return false;
            if(t>=1 && Math.abs(z)<.425 && y>=0 && y<=4.925) return true;
        }
        return false;
    }
    private static void launch() {
        var floor=new FlightCorridor.Box(-30,-2,-30,30,0,30);
        var roof=new FlightCorridor.Box(-5,2,-5,5,3,5);
        var wall=new FlightCorridor.Box(-5,0,3,5,5,4);
        var space=world(List.of(floor,roof,wall));
        var s=new FlightPrediction.State(0,.82,0,0,.33,0,0,0);
        var launch=LaunchPlanner.choose(s,0,.08,space);
        require(launch!=null,"launch from 2-block roofed space with side opening");
        require(Math.abs(launch.yaw())>=45,"blocked forward launch should choose side opening");
        var r=LaunchPlanner.request(launch.yaw(),launch.height(),26,.08);
        for(int tick=0;tick<12;tick++) {
            var p=GlidePlanner.choose(s,r.withPulse(26-tick),space);
            require(p.safe(),"launch lost corridor tick="+tick+" "+p);
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,true);
            require(space.clear(s,next,0,0),"launch collision"); s=next;
        }
        require(Math.abs(s.x())>5 || Math.abs(s.z())>5,"launch should leave shelter");
    }
}
