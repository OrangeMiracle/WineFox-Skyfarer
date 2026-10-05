package dev.winefox.skyfarer.flight;

/** Route-relative reserve and forward-simulation vetoes; no world or game bootstrap. */
public final class AttackEnvelopeChecks {
    private static final GlidePlanner.Corridor AIR=(a,b,s,v)->true;
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static FlightPrediction.State start(double y,double speed){return new FlightPrediction.State(0,y,0,0,0,speed,0,0);}
    private static GlidePlanner.Request route(double y,double rise,double distance,int rocket,double exitRise) {
        var aim=new FlightPrediction.Aim(0,y+rise-1,distance,.3,2,0,0,0,1,2.25,.125,8);
        return new GlidePlanner.Request(0,y+rise,distance,-rise/distance,-1,Double.NaN,rocket,5,6,10,.08,.15,.12,false,aim,
            new GlidePlanner.Exit(0,y+rise+exitRise,distance+10));
    }
    private static GlidePlanner.Forecast fly(FlightPrediction.State s,GlidePlanner.Request r) {
        return GlidePlanner.forecast(s,r,GlidePlanner.choose(s,r,AIR),AIR,.3);
    }
    public static void main(String[] args) {
        var ordinary=fly(start(100,1.5),route(100,0,32,0,3));
        require(ordinary.safe(),"ordinary coast attack and exit");
        var dive=fly(start(100,.6),route(100,-12,32,0,3));
        require(dive.safe(),"moderate starting kinetic energy can gain speed in a dive");
        var uphill=fly(start(100,1.5),route(100,12,32,0,3));
        require(GlidePlanner.energy(start(100,1.5),112,.08)>.5*.3*.3,"heuristic nominates uphill path");
        require(!uphill.safe(),"positive heuristic must not certify unreachable uphill contact");
        var cliff=fly(start(250,.15),route(250,30,32,0,3));
        require(GlidePlanner.energy(start(250,.15),0,.08)>20,"apparent valley energy");
        require(!cliff.safe()&&cliff.reserve()<0,"high absolute altitude cannot fund a cliff-wall entry");
        var low=fly(start(20,.15),route(20,30,32,0,3));
        require(Math.abs(low.reserve()-cliff.reserve())<1e-9,"envelope must be altitude-translation invariant");
        var shortPulse=fly(start(100,1.5),route(100,30,32,5,3));
        var longPulse=fly(start(100,1.5),route(100,30,32,22,3));
        require(!shortPulse.safe()&&longPulse.safe(),"active rocket remaining lifetime changes reachability");
        require(!fly(start(100,.15),route(100,0,32,0,3)).safe(),"inventory without ignition adds no thrust");
        require(fly(start(100,1.5),route(100,0,96,42,3)).safe(),"long approach includes eventual contact and exit");
        var noExit=fly(start(100,1.5),route(100,0,32,0,100));
        require(noExit.reserve()<ordinary.reserve()-1,"unreachable high exit demand must lower the full-route heuristic");
        var blockedExit=GlidePlanner.forecast(start(100,1.5),route(100,0,32,0,3),
            GlidePlanner.choose(start(100,1.5),route(100,0,32,0,3),AIR),(a,b,side,vertical)->b.z()<34,.3);
        require(!blockedExit.safe(),"strike is insufficient when the exit corridor is blocked");
        double worst=ordinary.envelope().samples().stream().mapToDouble(EnergyReserveEnvelope.Sample::reserve).min().orElseThrow();
        require(worst==ordinary.reserve(),"worst full-route reserve, not end state");
        require(ordinary.envelope().samples().stream().anyMatch(s->s.stage()==EnergyReserveEnvelope.Stage.EXIT),"exit included in envelope");
        for(int life=RocketProfile.minimumTicks(1);life<=RocketProfile.maximumTicks(1);life++)
            require(fly(start(100,1.5),route(100,12,32,life,3)).safe(),"full random lifetime "+life);
        var s=start(100,1.5);var path=SweepingAttackPlanner.candidates(s,new SweepingAttackPlanner.Point(0,100,24),8).getFirst();
        var aim=route(100,0,24,0,3).aim();
        var truncated=SweepingAttackPlanner.simulate(s,path,aim,160,.08,5,.3,false,AIR,(a,b,t)->true,10);
        require(!truncated.safe(),"horizon exhaustion cannot certify partial exit");
        var blocked=GlidePlanner.forecast(s,route(100,0,32,0,3),GlidePlanner.choose(s,route(100,0,32,0,3),AIR),AIR,.3,(a,b,t)->false);
        require(!blocked.safe(),"contact ray obstruction veto");
        var recovering=new FlightPrediction.State(0,4,0,0,-.15,.35,0,0);
        var turning=new GlidePlanner.Request(120,8,0,0,14,Double.NaN,0,5,3,8,.08,.25,.25,true,null);
        require(Double.isFinite(GlidePlanner.reviewPulse(recovering,turning,
            (a,b,side,vertical)->Math.min(a.y(),b.y())>.4+vertical,1,.8,.08).cost()),
            "a 120-degree recovery turn can be safe across every lifetime; heading heuristic must not overrule physics");
        var needy=start(100,.15);var target=new SweepingAttackPlanner.Point(0,100,24);
        require(SweepingAttackPlanner.choose(needy,target,aim,0,.08,5,.3,8,AIR,(a,b,t)->true)==null,
            "unlit inventory cannot certify an unpowered curve");
        var funded=SweepingAttackPlanner.choose(needy.withRocketDelay(1),target,aim,32,.08,5,.3,8,AIR,(a,b,t)->true);
        require(funded!=null,"full-exit candidate search must include a proposed inventory pulse");
        for(int life=21;life<=32;life++)require(SweepingAttackPlanner.simulate(needy.withRocketDelay(1),funded.path(),aim,life,.08,5,.3,false,AIR,(a,b,t)->true,120).safe(),
            "funded geometry still needs each real lifetime certified before ignition");
        System.out.println("Attack envelope checks passed: downhill/uphill, cliff, translation, low/high speed, long approach, coast/inventory/12 lifetimes, active expiry, exit reserve, horizon and physics veto.");
    }
}
