package dev.winefox.skyfarer.flight;

import dev.winefox.skyfarer.ai.AttackPolicy;
import dev.winefox.skyfarer.ai.SpearPassHistory;
import java.util.UUID;

public final class CombatRecoveryChecks {
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        exitsAndCharge(); slopes(); surfaces();
        System.out.println("Short exits, charge-tail deduplication/failure settlement, dense slope fits passed; no game launched.");
    }
    private static void exitsAndCharge() {
        for(double speed:new double[]{.25,.8,1.7,2.5}) for(double turn:new double[]{.1,2.5,5,20}) {
            double old=Math.max(12,Math.clamp(speed/Math.toRadians(turn),8,32)*1.5);
            double length=AttackPolicy.exitLength(12,speed,turn);
            require(Math.abs(length-old*2/3)<1e-9,"exit distance must be exactly 2/3 of former calculation");
            var capped=AttackExitPlanner.cap(0,1,0,0,30,old,length);
            require(Math.sqrt(capped.x()*capped.x()+Math.pow(capped.y()-1,2)+capped.z()*capped.z())<=length+1e-8,"climbing exit cap");
            require(AttackPolicy.exitComplete(6,true,5,length,true),"arrival must permit immediate reassessment before the 60-tick charge expires");
            require(AttackPolicy.exitComplete(20,false,length,length,true),"detours must not extend exit travel indefinitely");
            require(!AttackPolicy.exitComplete(20,true,length,length,false),"cannot leave unresolved emergency recovery");
        }
        var primary=UUID.randomUUID(); var secondary=UUID.randomUUID(); var history=new SpearPassHistory();
        history.begin(primary); history.hit(secondary); history.settle();
        require(history.avoided(1).contains(primary),"miss must be available to selection immediately after exit");
        require(history.alreadyHit(secondary) && !history.hit(secondary),"reassessment cannot reset tail deduplication");
        history.settle(); history.finish();
        require(!history.avoided(2).contains(primary),"tail expiry cannot count the same missed pass twice");
        history.begin(primary); history.settle();
        require(history.avoided(2).contains(primary),"second genuine miss must switch target");
        history.hit(primary); history.finish();
        require(!history.avoided(1).contains(primary),"late primary hit during tail corrects the settled failure");
        history.begin(secondary); require(!history.alreadyHit(secondary),"new attack resets hit set");
    }
    private static void slopes() {
        for(int radius:new int[]{16,32}) {
            var enlarged=SlopeSampler.sample(0,0,0,radius,.5,(x,z,y)->x*.25);
            require(enlarged.samples()==radius*16,"configured radius must not be silently capped at twelve");
            require(Math.abs(enlarged.wideX()-.25)<1e-8,"large-range slope fit");
        }
        for(double gx:new double[]{-.8,0,.75}) for(double gz:new double[]{-.7,.35,1.1}) {
            var exact=SlopeSampler.sample(.2,10,.3,6,.5,(x,z,previous)->10+gx*(x-.2)+gz*(z-.3));
            require(exact.samples()==96,"six-block half-step eight-spoke coverage");
            require(Math.abs(exact.wideX()-gx)<1e-8 && Math.abs(exact.wideZ()-gz)<1e-8,"oblique plane fit");
            var stairs=SlopeSampler.sample(0,0,0,6,.5,(x,z,previous)->Math.floor((gx*x+gz*z)*2)/2);
            require(Math.abs(stairs.wideX()-gx)<.06 && Math.abs(stairs.wideZ()-gz)<.06,"fractional stair accuracy");
        }
        var curve=SlopeSampler.sample(0,0,0,6,.5,(x,z,previous)->z<=3?z*.5:1.5+(z-3)*1.3);
        require(Math.abs(curve.nearZ()-curve.wideZ())>.08,"near slope must not be erased by a far slope change");
        var edge=SlopeSampler.sample(0,0,0,6,.5,(x,z,previous)->x>1?Double.NaN:z*.4);
        require(edge.samples()<96 && Double.isFinite(edge.wideZ()),"holes/unloaded columns stop only the affected profile");
        var empty=SlopeSampler.sample(0,0,0,6,.5,(x,z,previous)->Double.NaN);
        require(empty.samples()==0 && empty.wideX()==0 && empty.wideZ()==0,"no invented slope without support");
    }
    private static void surfaces() {
        for(double top:new double[]{.125,.25,.375,.5,.625,.75,.875}) {
            var boxes=java.util.List.of(new FlightCorridor.Box(0,-1,0,1,0,1),new FlightCorridor.Box(0,0,0,1,top,1),
                new FlightCorridor.Box(0,2,0,1,3,1));
            require(SupportSurface.nearest(boxes,.5,.5,0,1.26)==top,"exposed fractional top, not buried base/full block/roof: "+top);
        }
        var stair=java.util.List.of(new FlightCorridor.Box(0,0,0,1,.5,1),new FlightCorridor.Box(.5,.5,0,1,1,1));
        require(SupportSurface.nearest(stair,.25,.5,0,1.26)==.5,"lower stair tread");
        require(SupportSurface.nearest(stair,.75,.5,0,1.26)==1,"upper stair tread");
        var overhang=java.util.List.of(new FlightCorridor.Box(0,0,0,1,.5,1),new FlightCorridor.Box(0,2.5,0,1,3,1));
        require(SupportSurface.nearest(overhang,.5,.5,.5,1.26)==.5,"ceiling is not support");
        var upperSlab=java.util.List.of(new FlightCorridor.Box(0,-1,0,1,0,1),new FlightCorridor.Box(0,.5,0,1,1,1));
        require(SupportSurface.nearest(upperSlab,.5,.5,0,1.26,.6)==1,"cannot sample an inaccessible gap beneath an upper slab");
    }
}
