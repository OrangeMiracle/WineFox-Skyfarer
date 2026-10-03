package dev.winefox.skyfarer.flight;

import java.util.List;

/** Collision-shape and closed-loop flight replays. No Minecraft world or client is started. */
public final class GlideCorridorChecks {
    private static final FlightCorridor.Box FLOOR=new FlightCorridor.Box(-100,-2,-200,100,0,200);
    private static final FlightCorridor.Box ROOF=new FlightCorridor.Box(-2.5,3,-2.5,2.5,4,2.5);
    private static GlidePlanner.Corridor geometry(List<FlightCorridor.Box> boxes) {
        return (a,b,side,vertical) -> boxes.stream().noneMatch(box -> FlightCorridor.intersects(box,
            a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),0.3,0.6,0.4,side,vertical));
    }
    private static FlightPrediction.State state(double x,double y,double z,double speed,int pitch) {
        return new FlightPrediction.State(x,y,z,0,0,speed,0,pitch);
    }
    private static GlidePlanner.Request request(double height,int rocket,boolean recovery) {
        return new GlidePlanner.Request(0,height,0,0,14,Double.NaN,rocket,5,3,recovery?8:4,.08,.35,.35,recovery,null);
    }
    public static void main(String[] args) {
        var platform=geometry(List.of(FLOOR,ROOF));
        require(platform.clear(state(0,1.3,-10,1,0),state(0,1.3,10,1,0),.35,.35),"3-block platform tunnel should fit the swept maid");
        require(!platform.clear(state(0,2.7,-10,1,0),state(0,2.7,10,1,0),.35,.35),"roof must block a high pass");
        var centerPost=geometry(List.of(new FlightCorridor.Box(-.1,0,-.1,.1,2,.1)));
        require(!centerPost.clear(state(0,1,-3,1,0),state(0,1,3,1,0),.35,.35),"thin center post must not escape corner-ray sampling");
        var nearSide=geometry(List.of(new FlightCorridor.Box(.8,0,-1,1.8,3,1)));
        require(nearSide.clear(state(0,1,-3,1,0),state(0,1,3,1,0),.35,.35),"nearby blocks outside the corridor are irrelevant");
        var sealed=geometry(List.of(FLOOR,new FlightCorridor.Box(-5,.9,-2,5,2,2)));
        require(!sealed.clear(state(0,.6,-3,1,0),state(0,.6,3,1,0),.35,.35),"body plus safety margins must fit");
        int replays=0;
        for (double speed:new double[]{.8,1.2,1.7}) for (int rocket:new int[]{0,1,8,21,26,32,46,52}) {
            replay("platform",platform,speed,rocket,1.3,1.3); replays++;
            var slope=geometry(List.of(FLOOR,new FlightCorridor.Box(-100,0,5,100,1,200)));
            replay("slope",slope,speed,rocket,1.3,1.3); replays++;
        }
        var open=geometry(List.of());
        var high=state(0,20,0,.45,0); var low=state(0,1.3,0,.45,0);
        require(GlidePlanner.energy(high,1.3,.08)>GlidePlanner.energy(low,1.3,.08)+1,"altitude must count as stored energy");
        require(GlidePlanner.coastReserve(high,1.3,.08,0)>GlidePlanner.coastReserve(low,1.3,.08,0),"low speed alone does not imply low energy");
        var expired=low; var powered=low;
        for(int tick=0;tick<40;tick++) {
            expired=FlightPrediction.step(expired,0,-8,5,3,8,.08,tick<1);
            powered=FlightPrediction.step(powered,0,-8,5,3,8,.08,tick<32);
        }
        require(GlidePlanner.energy(powered,1.3,.08)>GlidePlanner.energy(expired,1.3,.08),"finite pulse lengths change usable energy");
        require(RocketProfile.maximumTicks(1)-RocketProfile.minimumTicks(1)==11,"all twelve random expiry times must be evaluated");
        var normal=GlidePlanner.choose(state(0,5,0,1.7,0),request(5,0,false),open);
        require(GlidePlanner.ordinary(normal),"no emergency in open air");
        checkExit("efficient straight glide",geometry(List.of(FLOOR)),AttackExitPlanner.Style.STRAIGHT_GLIDE);
        var wall=new FlightCorridor.Box(-3,0,18,3,12,20);
        checkExit("turn glide beside wall",geometry(List.of(FLOOR,wall)),AttackExitPlanner.Style.TURN_GLIDE);
        var ceiling=new FlightCorridor.Box(-100,3,-100,100,4,100);
        checkExit("turn glide",geometry(List.of(FLOOR,wall,ceiling)),AttackExitPlanner.Style.TURN_GLIDE);
        var brakeWorld=geometry(List.of(FLOOR,new FlightCorridor.Box(-100,0,5,100,6,6)));
        var braking=GlidePlanner.choose(state(0,1.3,0,1.7,0),request(1.3,26,true),brakeWorld);
        require(braking.mode()==GlidePlanner.Mode.AIRBRAKE,"pitch brake must precede reversal when overhead corridor is clear");
        var reverseWorld=geometry(List.of(FLOOR,new FlightCorridor.Box(-100,0,5,100,6,6),ceiling));
        var reversing=GlidePlanner.choose(state(0,1.3,0,1.7,0),request(1.3,26,true),reverseWorld);
        require(reversing.mode()==GlidePlanner.Mode.REVERSAL,"low roof should rule out pitch brake before emergency reversal");
        var impossible=geometry(List.of(FLOOR,new FlightCorridor.Box(-100,0,1,100,6,2),ceiling));
        require(!GlidePlanner.choose(state(0,1.3,0,1.7,0),request(1.3,0,true),impossible).safe(),"inertia must not be erased to claim an impossible escape");
        var pulseWorld=geometry(List.of(FLOOR,ceiling,new FlightCorridor.Box(-100,0,100,100,20,101)));
        require(Double.isFinite(GlidePlanner.pulseCost(state(0,1.3,0,1.2,0),request(1.3,0,false),pulseWorld,1,.8,.08)),"short pulse has a safe coast before the distant wall");
        require(!Double.isFinite(GlidePlanner.pulseCost(state(0,1.3,0,1.2,0),request(1.3,0,false),pulseWorld,3,.8,.08)),"long pulse must be rejected when expiry/recovery reaches the wall");
        int attacks=0;
        for(double speed:new double[]{.8,1.2,1.7}) for(int rocket:new int[]{0,26,46})
            for(double height:new double[]{1.3,5.3}) for(double drift:new double[]{0,12})
                for(double moving:new double[]{0,.1}) for(int delay:new int[]{10,20}) {
                    attackReplay(speed,rocket,height,drift,moving,delay); attacks++;
                }
        System.out.println(attacks+" new-planner attack/charge replays passed.");
        System.out.println(replays+" closed-loop corridor replays and swept-body/energy checks passed; Minecraft was not launched.");
    }
    private static void attackReplay(double speed,int rocket,double height,double drift,double moving,int delay) {
        double distance=Math.max(32,1.7*(delay+10)),slope=(height-1.3)/(distance-14);
        var s=new FlightPrediction.State(0,height,-distance,-speed*Math.sin(Math.toRadians(drift)),0,speed*Math.cos(Math.toRadians(drift)),0,0);
        var world=geometry(List.of(FLOOR,ROOF));
        int charge=-1,nextBoost=rocket>0?30:0; boolean hit=false;
        for(int tick=0;tick<120 && s.z()<3;tick++) {
            double closing=Math.max(rocket>tick?1.7:.6,Math.hypot(s.vx(),s.vz()));
            double lead=Math.clamp((-s.z()-2.25)/closing,0,12);
            double yaw=s.z()>=0?0:Math.toDegrees(Math.atan2(s.x()-moving*(tick+lead),-s.z()));
            var aim=new FlightPrediction.Aim(moving*tick,-3,0,.3,6.5,moving,0,0,1,2.25,.125,charge<0?delay:Math.max(0,delay-tick+charge));
            var r=new GlidePlanner.Request(yaw,1.3,Math.max(0,-s.z()),slope,14,Double.NaN,Math.max(0,rocket-tick),2.5,3,4,.08,.35,.35,false,aim);
            var p=GlidePlanner.choose(s,r,world);
            if (tick>=rocket && tick>=nextBoost && GlidePlanner.ordinary(p)) {
                var coast=GlidePlanner.forecast(s,r,p,world,.8);
                if ((!coast.safe() || coast.reserve()<.08) && Double.isFinite(GlidePlanner.pulseCost(s,r,world,1,.8,.08))) {
                    rocket=tick+26; nextBoost=tick+30; r=r.withPulse(26); p=GlidePlanner.choose(s,r,world);
                }
            }
            require(GlidePlanner.ordinary(p),"attack unnecessarily aborted: "+p+" state="+s);
            double a=Math.toRadians(FlightPrediction.turn(s.yaw(),p.yaw(),p.yawRate()));
            double b=Math.toRadians(FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate()));
            double dx=-Math.sin(a)*Math.cos(b),dy=-Math.sin(b),dz=Math.cos(a)*Math.cos(b);
            double far=2.25+Math.max(0,s.vx()*dx+s.vy()*dy+s.vz()*dz);
            if(charge>=0 && tick-charge>=delay) for(double t=1;t<=far;t+=.025) {
                double x=s.x()+dx*t,y=s.y()+dy*t,z=s.z()+dz*t;
                if(y<0) break; // native block clip stops at the floor
                if(Math.abs(x-moving*tick)<.425 && Math.abs(z)<.425 && y<=3.625) hit=true;
            }
            if(charge<0 && FlightMath.chargeDue(-s.z(),Math.max(1.7,closing),delay)) charge=tick;
            var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,tick<rocket);
            require(world.clear(s,next,0,0),"attack body collision"); s=next;
        }
        require(hit,"attack missed: speed="+speed+" height="+height+" drift="+drift+" moving="+moving+" delay="+delay+" state="+s);
    }
    private static void checkExit(String label,GlidePlanner.Corridor geometry,AttackExitPlanner.Style expected) {
        var s=state(0,1.3,0,1.7,0);
        var choice=AttackExitPlanner.choose(s,request(5.3,26,true),5.3,30,false,Double.NaN,Double.NaN,geometry,
            (x,y,z)->geometry.clear(s,state(x,y,z,0,0),.35,.35));
        require(choice!=null && choice.style()==expected,label+" choice="+choice);
    }
    private static void replay(String label,GlidePlanner.Corridor geometry,double speed,int rocket,double height,double goal) {
        var s=state(0,height,-35,speed,0); int emergencies=0, nextBoost=rocket>1?30:0;
        for (int tick=0;tick<150 && s.z()<15;tick++) {
            var r=request(goal,Math.max(0,rocket-tick),false);
            var plan=GlidePlanner.choose(s,r,geometry);
            var coast=GlidePlanner.forecast(s,r,plan,geometry,.8);
            if (GlidePlanner.ordinary(plan) && tick>=rocket && tick>=nextBoost && (!coast.safe() || coast.reserve()<.08)) {
                int duration=-1; double best=Double.POSITIVE_INFINITY;
                for (int d:new int[]{1,2,3}) {
                    double cost=GlidePlanner.pulseCost(s,r,geometry,d,.8,.08);
                    if(cost<best) { best=cost; duration=d; }
                }
                if (duration>=0) {
                    rocket=tick+(int)RocketProfile.expectedTicks(duration); nextBoost=tick+30;
                    r=r.withPulse(rocket-tick); plan=GlidePlanner.choose(s,r,geometry);
                }
            }
            if(!GlidePlanner.ordinary(plan)) emergencies++;
            var next=FlightPrediction.step(s,plan.yaw(),plan.pitch(),plan.yawRate(),plan.pitchRate(),plan.pitchRate(),.08,tick<rocket);
            require(geometry.clear(s,next,0,0),label+" physical collision speed="+speed+" rocket="+rocket+" tick="+tick+" plan="+plan+" state="+s);
            s=next;
        }
        require(s.z()>=15,label+" failed to traverse speed="+speed+" rocket="+rocket+" state="+s);
        require(emergencies==0,label+" unnecessary emergency count="+emergencies+" speed="+speed+" rocket="+rocket);
    }
    private static void require(boolean result,String message) { if(!result) throw new AssertionError(message); }
}
