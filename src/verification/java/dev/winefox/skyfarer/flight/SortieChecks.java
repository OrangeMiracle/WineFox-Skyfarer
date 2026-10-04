package dev.winefox.skyfarer.flight;

/** Segment physics checks; ControllerReplayChecks covers the actual full state-machine lifecycle. */
public final class SortieChecks {
    private static void require(boolean value,String why) { if(!value) throw new AssertionError(why); }
    private static final GlidePlanner.Corridor FLOOR=(a,b,side,vertical)->Math.min(a.y(),b.y())>.6+vertical;
    public static void main(String[] args) {
        compact(); dives(); chains();
        System.out.println("Bounded kinetic-to-height turns, curved dive contacts, lateral chain contact/recovery and blocked-chain rejection passed; no game launched.");
    }
    private static void compact() {
        for(double speed:new double[]{.4,.9,1.7}) for(int rocket:new int[]{0,12,38,47}) for(double side:new double[]{0,14}) {
            var s=new FlightPrediction.State(0,12,0,0,0,speed,0,0);
            var goal=new CompactTurnPlanner.Goal(side,22,-14,0,0,48);
            double peak=s.y(),slowest=speed,furthest=0; boolean aligned=false;
            for(int t=0;t<55;t++) {
                var p=CompactTurnPlanner.choose(s,goal,Math.max(0,rocket-t),.08,5,FLOOR);
                require(p!=null,"no compact turn speed="+speed+" rocket="+rocket+" side="+side+" tick="+t+" state="+s);
                var c=p.command();
                var next=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),.08,t<rocket);
                require(FLOOR.clear(s,next,.04,.04),"compact turn ground strike"); s=next;
                peak=Math.max(peak,s.y()); slowest=Math.min(slowest,Math.hypot(s.vx(),s.vz()));
                furthest=Math.max(furthest,Math.hypot(s.x(),s.z()));
                if(CompactTurnPlanner.angle(s,goal.x(),goal.z())<22) { aligned=true; break; }
            }
            require(aligned && furthest<48,"must turn locally, not wait for rocket expiry: "+speed+"/"+rocket+"/"+side);
            require(peak<30,"bounded goal must not become a permanent rocket climb");
            if(speed>.8 && rocket==0) require(peak>13 && slowest<speed*.85,"must exchange momentum for height");
        }
        var low=new FlightPrediction.State(0,2,0,0,-.1,.3,0,-10);
        var goal=new CompactTurnPlanner.Goal(0,7,-12,0,0,32);
        require(Double.isFinite(CompactTurnPlanner.pulseCost(low,goal,1,.08,5,FLOOR)),"low-energy turning climb has a safe finite pulse");
        require(!Double.isFinite(CompactTurnPlanner.pulseCost(low,goal,1,.08,5,(a,b,side,vertical)->false)),"reject enclosed pulse");
    }
    private static void dives() {
        for(double speed:new double[]{.6,1,1.5}) for(double rise:new double[]{5,9,13}) {
            double distance=Math.max(28,DiveProfile.minimumDistance(rise,speed,.08)),slope=DiveProfile.slope(rise,distance);
            var s=new FlightPrediction.State(0,2+rise,0,0,-.1,speed,0,15);
            var aim=new FlightPrediction.Aim(0,-3,distance,.3,7.8,0,0,0,0,2.25,.1,10);
            boolean hit=false; double down=0;
            for(int tick=0;tick<90;tick++) {
                double remaining=distance-s.z();
                var r=new GlidePlanner.Request(0,2,remaining,slope,-2,Double.NaN,0,2.5,6,10,.08,.35,.35,false,aim.after(tick))
                    .withExit(new GlidePlanner.Exit(0,7,distance+8));
                if(remaining< -2.25) r=new GlidePlanner.Request(0,7,0,0,14,Double.NaN,0,5,6,10,.08,.35,.35,true,null);
                var p=GlidePlanner.choose(s,r,FLOOR);
                if(Boolean.getBoolean("wfs.trace") && speed==1.5 && rise==13) System.out.println("dive tick="+tick+" z="+s.z()+" y="+s.y()+" vy="+s.vy()+" pitch="+p.pitch()+" miss="+aim.missSquared(s,tick));
                require(GlidePlanner.ordinary(p),"curve requires emergency speed="+speed+" rise="+rise+" tick="+tick);
                var looked=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),p.yaw(),p.yawRate()),FlightPrediction.turn(s.pitch(),p.pitch(),p.pitchRate()));
                if(tick>=10 && aim.missSquared(looked,tick)<.015) hit=true;
                var next=FlightPrediction.step(s,p.yaw(),p.pitch(),p.yawRate(),p.pitchRate(),p.pitchRate(),.08,false);
                require(FLOOR.clear(s,next,.04,.04),"curved dive hit floor");
                down=Math.max(down,-next.vy()); s=next;
                if(s.z()>distance+12) break;
            }
            require(hit && down>.025*rise,"curved dive must descend and contact: speed="+speed+" rise="+rise+" hit="+hit+" down="+down+" end="+s);
        }
        require(FlightPrediction.pathHeight(-3,2,.8,-2)==2,"curve cannot continue through floor after contact");
        require(Math.abs(FlightPrediction.pathVerticalSpeed(new FlightPrediction.State(0,2,0,0,0,1,0,0),0,.8,-2))<1e-9,"smooth bottom tangent");
    }
    private static void chains() {
        for(int sign:new int[]{-1,1}) for(double speed:new double[]{.8,1.1}) {
            var s=new FlightPrediction.State(0,2,0,0,0,speed,0,0);
            var aim=new FlightPrediction.Aim(sign*3,-3,5,.3,7.8,0,0,0,0,2.25,.1,0);
            boolean hit=false;
            for(int t=0;t<25;t++) {
                var p=ChainAttackPlanner.choose(s,aim,2,0,.08,.3,32-t,18,FLOOR);
                require(p!=null,"lateral chain unreachable speed="+speed+" sign="+sign+" tick="+t);
                var c=p.command();
                var looked=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),c.yaw(),c.yawRate()),FlightPrediction.turn(s.pitch(),c.pitch(),c.pitchRate()));
                if(aim.missSquared(looked,0)<.015) { hit=true; break; }
                s=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),.08,false);
            }
            require(hit,"chain must contact during closed-loop turn");
            var third=new FlightPrediction.Aim(sign*2,-3,5,.3,7.8,0,0,0,0,2.25,.1,0);
            require(ChainAttackPlanner.choose(s,third,2,0,.08,.3,20,12,FLOOR)!=null,"third target must be considered");
        }
        var s=new FlightPrediction.State(0,2,0,0,0,.8,0,0);
        var target=new FlightPrediction.Aim(3,-3,6,.3,7.8,0,0,0,0,2.25,.1,0);
        require(ChainAttackPlanner.choose(s,target,2,0,.08,.3,32,18,(a,b,side,vertical)->false)==null,"blocked chain must pull out");
        require(ChainAttackPlanner.choose(s,target,2,0,.08,.3,0,18,FLOOR)==null,"expired chain budget");
        var slow=new FlightPrediction.State(0,2,0,0,0,.5,0,0);
        require(ChainAttackPlanner.choose(slow,target,2,0,.08,.3,32,18,FLOOR)==null,"low energy without a recoverable exit must end chaining");
        var fast=new FlightPrediction.State(0,2,0,0,0,1.4,0,0);
        var behind=new FlightPrediction.Aim(0,-3,-6,.3,7.8,0,0,0,0,2.25,.1,0);
        require(ChainAttackPlanner.choose(fast,behind,2,0,.08,.3,8,18,FLOOR)==null,"unreachable reversal within remaining budget must not start a chase loop");
    }
}
