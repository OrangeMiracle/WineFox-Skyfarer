package dev.winefox.skyfarer.flight;

import java.util.List;

/** Representative reachability/capture counterexamples, independent of any live entity pose. */
public final class AttackFunnelChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final List<FlightCorridor.Box> HILL=List.of(new FlightCorridor.Box(-96,-10,-96,96,0,96),
        new FlightCorridor.Box(-40,0,-40,0,28,40),new FlightCorridor.Box(-10,0,-16,8,24,-8),new FlightCorridor.Box(0,0,9.5,1.5,12,10.5));
    private static final GlidePlanner.Corridor SPACE=(a,b,s,v)->HILL.stream().noneMatch(box->FlightCorridor.intersects(box,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),.6,.6,.4,s,v));
    private static final SweepingAttackPlanner.Contact CONTACT=(a,b,t)->HILL.stream().noneMatch(box->FlightCorridor.intersects(box,a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()/2+b.vy()*t,b.z()+b.vz()*t,0,0,0,0,0));
    private static final GlidePlanner.Corridor AIR=(a,b,s,v)->b.y()>.6;
    private static final SweepingAttackPlanner.Contact RAY=(a,b,t)->true;
    public static void main(String[] args){
        var aim=new FlightPrediction.Aim(1,9,10,.3,7.8,0,0,0,0,2.25,.1,10);
        var input=new AttackFunnelPlanner.Input(new SweepingAttackPlanner.Point(1,13.4,10),aim,PhysicsContext.VANILLA,5,.3,8);
        var hill=AttackFunnelPlanner.discover(input,SPACE,CONTACT);
        require(!hill.windows().isEmpty(),"hillside has no independently generated target window");
        var curved=hill.windows().stream().filter(w->w.path().style.contains("ARC")).findFirst().orElseThrow();
        require(curved.proof().safe()&&curved.proof().hit(),"curved window lacks a full physical strike/exit proof");
        var reverse=AttackFunnelPlanner.reverse(curved.path());
        require(reverse.clear(SPACE,.15,.12),"asymmetry fixture must retain identical clear geometry");
        var backwards=SweepingAttackPlanner.simulate(AttackFunnelPlanner.witness(reverse,1.3,PhysicsContext.VANILLA),reverse,aim,0,.08,5,.3,false,SPACE,CONTACT,120);
        require(!backwards.safe(),"geometric reversal incorrectly inherited forward physics proof");
        require(AttackFunnelPlanner.discover(input,SPACE,(a,b,t)->false).windows().isEmpty(),"blocked strike ray accepted");
        var open=AttackFunnelPlanner.discover(input,AIR,RAY);var w=open.windows().getFirst();var e=w.witness();
        var one=new AttackFunnelPlanner.Atlas(List.of(w));
        var slow=new FlightPrediction.State(e.x(),e.y(),e.z(),0,-.05,.15,e.yaw(),e.pitch(),PhysicsContext.VANILLA);
        require(w.spatial(slow)&&!w.dynamic(slow,0),"spatial inclusion permitted an under-speed commitment");
        require(!w.dynamic(e,8),"active rocket state ignored by coast window");
        var longWindup=new FlightPrediction.Aim(1,9,10,.3,7.8,0,0,0,0,2.25,.1,20);
        var overshoot=SweepingAttackPlanner.simulate(e.withRocketDelay(1),w.path(),longWindup,22,.08,5,.3,false,AIR,RAY,120);
        require(!overshoot.hit()&&!overshoot.safe()&&overshoot.trace().stream().anyMatch(p->p.state().z()>aim.z()+4),"rocket did not expose pre-windup strike overshoot");
        require(AttackFunnelPlanner.capture(e,one,longWindup,0,5,.3,List.of(0),true,AIR,RAY)==null,"overshooting pulse accepted for Capture");
        var needy=new FlightPrediction.State(e.x(),e.y(),e.z()-30,0,-.05,.15,e.yaw(),e.pitch(),PhysicsContext.VANILLA);
        require(AttackFunnelPlanner.capture(needy,one,aim,0,5,.3,List.of(),false,AIR,RAY)==null,"fixture must actually need propulsion");
        var funded=AttackFunnelPlanner.capture(needy,one,aim,0,5,.3,List.of(2,0,1),false,AIR,RAY);
        require(funded!=null&&funded.duration()==0&&funded.igniteIn()==0&&funded.captureTicks()>0,"smallest sufficient inventory pulse not selected");
        // Independently execute every possible life through Capture, then fresh windup Rejoin.
        for(int life=11;life<=22;life++){
            var s=needy.withRocketDelay(1);int remaining=life;boolean joined=false;
            for(int tick=0;tick<=64;tick++){
                if(w.dynamic(s,remaining)){
                    var proof=SweepingAttackPlanner.rejoin(s,w.path(),aim,remaining,.08,5,.3,AIR,RAY);
                    if(proof!=null){joined=true;break;}
                }
                var c=AttackFunnelPlanner.captureCommand(s,w,remaining,.08,5);
                boolean powered=remaining>0&&s.rocketDelay()==0;
                s=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),.08,powered);
                if(powered)remaining--;
            }
            require(joined,"inventory lifetime "+life+" failed independent capture replay");
        }
        var coast=AttackFunnelPlanner.capture(e,one,aim,0,5,.3,List.of(0),false,AIR,RAY);
        require(coast!=null&&coast.duration()==-1,"safe coast unnecessarily ignited inventory");
        require(w.tube().size()<=18&&open.windows().size()<=4,"debug/search bounds exceeded");
        System.out.println("Attack funnels: curved hillside, separately simulated reverse, dynamic gate, overshoot veto, coast-first and all 12 small-pulse captures passed.");
    }
}
