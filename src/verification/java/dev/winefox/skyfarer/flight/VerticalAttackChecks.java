package dev.winefox.skyfarer.flight;

import java.util.*;

/** A small set of real-physics Strike -> Exit rollouts, not fixed-angle kinematic paths. */
public final class VerticalAttackChecks {
    public static void main(String[] args){
        for(boolean climb:new boolean[]{false,true}) {
            var start=new FlightPrediction.State(0,60,0,0,climb?1.4:-1.2,climb?.6:.8,0,climb?-65:60);
            var target=new SweepingAttackPlanner.Point(0,climb?80:35,climb?14:18);
            var aim=new FlightPrediction.Aim(target.x(),target.y()-.9,target.z(),.3,1.8,0,0,0,0,2.25,.1,10);
            var paths=SweepingAttackPlanner.candidates(start,target,12);
            require(paths.size()<=216,"candidate budget");
            for(double angle:new double[]{-70,-60,-50,50,60,70})require(paths.stream().anyMatch(p->{
                var d=p.entry.d().subtract(p.entry.c());return Math.abs(Math.toDegrees(Math.atan2(d.y(),Math.hypot(d.x(),d.z())))-angle)<1e-6;
            }),"missing signed steep candidate "+angle);
            SweepingAttackPlanner.Selection best=null;
            for(var p:paths)if(p.style.startsWith(climb?"VERTICAL_CLIMB":"VERTICAL_DIVE")){
                var result=SweepingAttackPlanner.simulate(start,p,aim,climb?32:0,.08,5,.2,false,VerticalAttackChecks::clear,(s,a,t)->true,120);
                if(!result.safe()||!result.hit())continue;
                double pitch=result.trace().get(result.contactTick()).state().pitch();
                if(Math.abs(pitch)<50||Math.abs(pitch)>70||climb&&pitch>=0||!climb&&pitch<=0)continue;
                boolean all=true;
                if(climb)for(int remaining=21;remaining<=32;remaining++)all&=SweepingAttackPlanner.simulate(start,p,aim,remaining,.08,5,.2,false,VerticalAttackChecks::clear,(s,a,t)->true,120).safe();
                if(all&&(best==null||result.cost()<best.prediction().cost()))best=new SweepingAttackPlanner.Selection(p,result);
            }
            require(best!=null,"no certified vertical "+(climb?"climb":"dive"));
            var result=best.prediction();var contact=result.trace().get(result.contactTick()).state();
            System.out.println((climb?"climb":"dive")+" contact="+result.contactTick()+" pitch="+contact.pitch()+" style="+best.path().style+" exitTicks="+result.trace().size());
            require(result.trace().size()>result.contactTick()+16,"partial exit accepted");
            if(climb)require(Math.abs(contact.yaw()-start.yaw())>5,"vertical turning candidate absent");
            require(!SweepingAttackPlanner.simulate(start,best.path(),aim,climb?32:0,.08,5,.2,false,(a,b,s,v)->false,(s,a,t)->true,120).safe(),"body collision bypass");
            require(!SweepingAttackPlanner.simulate(start,best.path(),aim,climb?32:0,.08,5,.2,false,VerticalAttackChecks::clear,(s,a,t)->false,120).safe(),"blocked spear ray bypass");
        }
        var slope=SlopeSampler.sample(0,40,0,8,.5,(x,z,y)->40+z*Math.tan(Math.toRadians(70)));
        require(Math.abs(slope.nearZ()-AttackLinePlanner.MAX_GRADE)<1e-8,"70 degree slope was flattened");
    }
    private static boolean clear(FlightPrediction.State a,FlightPrediction.State b,double side,double vertical){return Math.min(a.y(),b.y())>2+vertical&&Math.abs(b.x())<180&&Math.abs(b.z())<180;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
