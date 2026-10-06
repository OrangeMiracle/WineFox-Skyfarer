package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static dev.winefox.skyfarer.flight.SweepingAttackPlanner.*;

/** Target-centred sampled reachability map. A window is a search hint, never a commit certificate. */
public final class AttackFunnelPlanner {
    private AttackFunnelPlanner() {}
    public static final int MAX_WINDOWS=4, MAX_CAPTURE_TICKS=64;
    public record Input(Point target, FlightPrediction.Aim aim, PhysicsContext physics,
                        double rate, double minimum, double exitLength) {}
    /** Tolerances nominate nearby states for forward Rejoin; they do not prove a continuous volume. */
    public record Window(int id, Path path, FlightPrediction.State witness, int rocket,
                         double radius, double minimumSpeed, double maximumSpeed,
                         double headingTolerance, double pitchTolerance, double verticalTolerance,
                         Rollout proof, List<Point> tube) {
        public Window {tube=List.copyOf(tube);}
        public boolean spatial(FlightPrediction.State s) {
            var a=path.entry.a();var delta=path.entry.at(.3).subtract(a);var offset=point(s).subtract(a);
            double t=Math.clamp((offset.x()*delta.x()+offset.y()*delta.y()+offset.z()*delta.z())/Math.max(.001,delta.length()*delta.length()),0,1);
            return offset.subtract(delta.scale(t)).length()<=radius;
        }
        public boolean dynamic(FlightPrediction.State s,int remaining) {
            double speed=Math.hypot(s.vx(),s.vz());
            double velocityYaw=Math.toDegrees(Math.atan2(-s.vx(),s.vz()));
            return spatial(s)&&speed>=minimumSpeed&&speed<=maximumSpeed
                &&angle(s.yaw(),witness.yaw())<=headingTolerance&&angle(velocityYaw,witness.yaw())<=headingTolerance
                &&Math.abs(s.pitch()-witness.pitch())<=pitchTolerance&&Math.abs(s.vy()-witness.vy())<=verticalTolerance
                &&(rocket==0?remaining==0:remaining>0&&remaining<=12)
                &&s.rocketDelay()==0;
        }
    }
    public record Atlas(List<Window> windows) {public Atlas {windows=List.copyOf(windows);}}
    public record Capture(Window window,int duration,int igniteIn,int captureTicks,double cost,
                          Command first,List<GlidePlanner.TracePoint> trace) {
        public Capture {trace=List.copyOf(trace);}
    }
    private static double angle(double a,double b){return Math.abs(FlightPrediction.turn(a,b,180)-a);}
    /** Both orientations are geometry only. Each must receive its OWN forward proof below. */
    public static Path reverse(Path path) {
        var a=path.exit;var b=path.entry;
        return new Path(new Cubic(a.d(),a.c(),a.b(),a.a()),new Cubic(b.d(),b.c(),b.b(),b.a()),path.style+"_REVERSE",path.simplicity);
    }
    public static List<Path> templates(Input in) {
        var paths=new ArrayList<Path>();
        double base=Math.clamp(Math.max(20,in.aim.readyIn()*1.25+in.aim.maxReach()+7),20,38);
        for(int axis=0;axis<4;axis++)for(double bend:new double[]{0,-.65,.65})for(double length:new double[]{base,38})for(double height:new double[]{2,6}) {
            double yaw=Math.toRadians(axis*45);var forward=new Point(-Math.sin(yaw),0,Math.cos(yaw));
            var side=new Point(forward.z(),0,-forward.x());
            var strike=in.target;
            var a=strike.subtract(forward.scale(length)).add(side.scale(bend*length*.5)).add(new Point(0,height,0));
            var b=strike.add(forward.scale(length)).add(side.scale(bend*length*.45)).add(new Point(0,2,0));
            var startTangent=forward.add(side.scale(bend)).unit();
            var endTangent=forward.add(side.scale(bend*.5)).unit();
            double handle=length*.55,join=Math.max(5,in.exitLength*.65);
            var path=new Path(new Cubic(a,a.add(startTangent.scale(handle)),strike.subtract(forward.scale(join)),strike),
                new Cubic(strike,strike.add(forward.scale(join)),b.subtract(endTangent.scale(handle)),b),
                bend==0?"FUNNEL_DIRECT":bend<0?"FUNNEL_LEFT_ARC":"FUNNEL_RIGHT_ARC",bend==0?0:1.5);
            paths.add(path);paths.add(reverse(path));
        }
        // Shoulder routes reuse the existing primitive, including the lateral spear contact.
        // These starts are target-relative, never copied from the approaching maid.
        for(int heading=0;heading<8;heading++)for(int side:new int[]{-1,1})for(double height:new double[]{.6,4}){
            double a=Math.toRadians(heading*45);var forward=new Point(-Math.sin(a),0,Math.cos(a));
            var right=new Point(forward.z(),0,-forward.x());
            var origin=in.target.subtract(forward.scale(38)).add(right.scale(side*8)).add(new Point(0,height,0));
            var tangent=forward.scale(Math.cos(Math.toRadians(35))).add(right.scale(side*Math.sin(Math.toRadians(35))));
            var start=new FlightPrediction.State(origin.x(),origin.y(),origin.z(),tangent.x()*1.3,-.05,tangent.z()*1.3,
                Math.toDegrees(Math.atan2(-tangent.x(),tangent.z())),0,in.physics);
            var candidate=SweepingAttackPlanner.candidate(start,in.target,in.exitLength,side*30,.55,0,side*1.2);
            var path=new Path(candidate.entry,candidate.exit,"FUNNEL_"+candidate.style,candidate.simplicity);
            paths.add(path);paths.add(reverse(path));
        }
        return List.copyOf(paths);
    }
    public static FlightPrediction.State witness(Path path,double speed,PhysicsContext physics) {
        var a=path.entry.a();var tangent=path.entry.b().subtract(a).unit();
        return new FlightPrediction.State(a.x(),a.y(),a.z(),tangent.x()*speed,-.05,tangent.z()*speed,
            Math.toDegrees(Math.atan2(-tangent.x(),tangent.z())),0,physics);
    }
    public static Atlas discover(Input in,GlidePlanner.Corridor space,Contact contact) {
        var windows=new ArrayList<Window>();int id=0;
        // 160 geometries x 2 speeds x at most 2 rocket states. No dependence on maid pose.
        for(var path:templates(in)) {
            int identity=id++;
            if(!path.clear(space,.15,.12))continue;
            Window best=null;
            for(double speed:new double[]{.85,1.3})for(int rocket:new int[]{0,8}) {
                var start=witness(path,speed,in.physics);
                var proof=simulate(start,path,in.aim,rocket,in.physics.gravity(),in.rate,in.minimum,false,space,contact,120,false);
                if(!proof.safe()||!proof.hit())continue;
                var candidate=new Window(identity,path,start,rocket,4,Math.max(in.minimum,speed-.4),speed+.45,35,35,.5,proof,tube(path));
                if(best==null||rocket<best.rocket||rocket==best.rocket&&proof.cost()<best.proof.cost())best=candidate;
                if(rocket==0)break;
            }
            if(best!=null)windows.add(best);
        }
        windows.sort(Comparator.comparingDouble(w->w.proof.cost()+w.rocket*2));
        // Keep different entry directions instead of four nearly identical speed/height samples.
        var diverse=new ArrayList<Window>();
        for(var w:windows) {
            if(diverse.stream().anyMatch(old->angle(old.witness.yaw(),w.witness.yaw())<25))continue;
            var traced=simulate(w.witness,w.path,in.aim,w.rocket,in.physics.gravity(),in.rate,in.minimum,false,space,contact,120);
            diverse.add(new Window(w.id,w.path,w.witness,w.rocket,w.radius,w.minimumSpeed,w.maximumSpeed,w.headingTolerance,w.pitchTolerance,w.verticalTolerance,traced,w.tube));
            if(diverse.size()==MAX_WINDOWS)break;
        }
        return new Atlas(diverse);
    }
    private static List<Point> tube(Path path) {
        var out=new ArrayList<Point>();var points=path.points();
        int stride=Math.max(1,(int)Math.ceil((points.size()-1)/16.0));
        for(int i=0;i<points.size()-1;i+=stride)out.add(points.get(i));
        out.add(points.getLast());return List.copyOf(out);
    }
    /** Cheap feedback also used by the main thread; all search and proof stays on workers. */
    public static Command captureCommand(FlightPrediction.State s,Window w,int rocket,double gravity,double rate) {
        var entry=w.path.entry.a();var tangent=w.path.entry.b().subtract(entry).unit();
        var offset=point(s).subtract(entry);double along=offset.x()*tangent.x()+offset.z()*tangent.z();
        double lateral=offset.x()*tangent.z()-offset.z()*tangent.x();
        // Acquire an upstream alignment point before crossing the entry plane from the side.
        var goal=entry.add(tangent.scale(Math.abs(lateral)>7||along>4?-10:5));
        double speed=Math.hypot(s.vx(),s.vz());
        double desiredVy=Math.clamp((entry.y()-s.y())*.08,-.3,.3);
        double pitch=GlidePlanner.feedbackPitch(s,entry.y(),desiredVy,gravity,rocket>0);
        double yaw=Math.toDegrees(Math.atan2(s.x()-goal.x(),goal.z()-s.z()));
        if(speed>w.maximumSpeed&&angle(s.yaw(),yaw)>35&&s.y()<entry.y()+3)pitch=Math.min(pitch,-30);
        if(rocket==0&&speed<.65)pitch=Math.max(pitch,-6);
        return new Command(yaw,Math.clamp(pitch,-55,45),rate,6);
    }
    public static Window nearest(FlightPrediction.State s,Atlas atlas) {
        return atlas.windows.stream().min(Comparator.comparingDouble(w->point(s).subtract(point(w.witness)).length()
            +angle(s.yaw(),w.witness.yaw())*.12+Math.abs(Math.hypot(s.vx(),s.vz())-Math.hypot(w.witness.vx(),w.witness.vz()))*8)).orElse(null);
    }
    /** Coast first; only real inventory durations reach this function. Every lifetime must pass. */
    public static Capture capture(FlightPrediction.State start,Atlas atlas,FlightPrediction.Aim aim,int rocket,
                                  double rate,double minimum,List<Integer> durations,boolean immediateOnly,
                                  GlidePlanner.Corridor space,Contact contact) {
        Capture best=null;
        for(var w:atlas.windows) {
            var coast=trial(start,w,aim,rocket,rate,minimum,-1,-1,0,space,contact);
            if(coast!=null&&(best==null||coast.cost<best.cost))best=coast;
        }
        if(best!=null||rocket>0)return best;
        for(int duration:durations.stream().filter(d->d>=0&&RocketProfile.maximumTicks(d)<=80).distinct().sorted().toList()) {
            for(var w:atlas.windows)for(int when:immediateOnly?new int[]{0}:new int[]{0,8,16}) {
                Capture candidate=null;double cost=0;boolean safe=true;
                for(int life=RocketProfile.minimumTicks(duration);life<=RocketProfile.maximumTicks(duration);life++) {
                    var result=trial(start,w,aim,0,rate,minimum,duration,when,life,space,contact);
                    if(result==null){safe=false;break;}
                    cost+=result.cost;candidate=result;
                }
                if(safe) {
                    var result=new Capture(w,duration,when,candidate.captureTicks,cost/12,candidate.first,candidate.trace);
                    if(best==null||result.cost<best.cost)best=result;
                }
            }
            if(best!=null)return best; // minimal sufficient inventory pulse, not fastest pulse
        }
        return null;
    }
    private static Capture trial(FlightPrediction.State start,Window w,FlightPrediction.Aim aim,int active,
                                 double rate,double minimum,int duration,int when,int life,
                                 GlidePlanner.Corridor space,Contact contact) {
        var s=start;var trace=new ArrayList<GlidePlanner.TracePoint>();trace.add(new GlidePlanner.TracePoint(s,.15,.12));
        Command first=null;double gravity=start.physics()==null?.08:start.physics().gravity();
        int remaining=active,delay=start.rocketDelay();
        for(int tick=0;tick<=MAX_CAPTURE_TICKS;tick++) {
            if(duration>=0&&tick==when){remaining=life;delay=1;s=s.withRocketDelay(1);}
            if((duration<0||tick>=when)&&w.dynamic(s,remaining)) {
                // Capture is not charging. Its future Rejoin starts a NEW full native windup.
                var moving=aim.after(tick);
                var fresh=new FlightPrediction.Aim(moving.x(),moving.bottom(),moving.z(),moving.halfWidth(),moving.height(),
                    moving.vx(),moving.vy(),moving.vz(),moving.minReach(),moving.maxReach(),moving.margin(),aim.readyIn());
                var proof=simulate(s,w.path,fresh,remaining,gravity,rate,minimum,false,space,contact,120);
                if(proof.safe()&&proof.hit()) {
                    trace.addAll(proof.trace());
                    return new Capture(w,duration,when,tick,tick+proof.cost(),first==null?proof.first():first,trace);
                }
            }
            if(tick==MAX_CAPTURE_TICKS)break;
            var c=captureCommand(s,w,remaining,gravity,rate);if(first==null)first=c;
            var next=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),gravity,remaining>0&&delay==0);
            if(!space.clear(s,next,.15,.12))return null;
            if(delay>0)delay--;else if(remaining>0)remaining--;
            s=next.withRocketDelay(delay);trace.add(new GlidePlanner.TracePoint(s,.15,.12));
        }
        return null;
    }
}
