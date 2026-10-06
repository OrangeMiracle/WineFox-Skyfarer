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
    /** Empirical envelope of successful rollouts, not a continuous collision certificate. */
    public record Section(Point center,double width,double height,double yaw,double pitch,
                          double headingTolerance,double pitchTolerance,double minimumVy,double maximumVy,
                          double minimumSpeed,double maximumSpeed,boolean capture) {
        public boolean spatial(FlightPrediction.State s){
            return Math.hypot(s.x()-center.x(),s.z()-center.z())<=width&&Math.abs(s.y()-center.y())<=height;
        }
        public boolean dynamic(FlightPrediction.State s){
            double speed=Math.hypot(s.vx(),s.vz());
            return capture&&spatial(s)&&speed>=minimumSpeed&&speed<=maximumSpeed
                &&angle(s.yaw(),yaw)<=headingTolerance&&angle(Math.toDegrees(Math.atan2(-s.vx(),s.vz())),yaw)<=headingTolerance
                &&Math.abs(s.pitch()-pitch)<=pitchTolerance&&s.vy()>=minimumVy&&s.vy()<=maximumVy;
        }
    }
    /** Tolerances nominate nearby states for forward Rejoin; they do not prove a continuous volume. */
    public record Window(int id, Path path, FlightPrediction.State witness, int rocket,
                         double radius, double minimumSpeed, double maximumSpeed,
                         double headingTolerance, double pitchTolerance, double verticalTolerance,
                         Rollout proof, List<Point> tube,List<Section> sections,List<FlightPrediction.State> seeds) {
        public Window {tube=List.copyOf(tube);sections=List.copyOf(sections);seeds=List.copyOf(seeds);}
        public Window(int id,Path path,FlightPrediction.State witness,int rocket,double radius,double minimumSpeed,double maximumSpeed,
                      double headingTolerance,double pitchTolerance,double verticalTolerance,Rollout proof,List<Point> tube){
            this(id,path,witness,rocket,radius,minimumSpeed,maximumSpeed,headingTolerance,pitchTolerance,verticalTolerance,proof,tube,List.of(),List.of(witness));
        }
        public boolean spatial(FlightPrediction.State s) {
            if(!sections.isEmpty())return sections.stream().anyMatch(p->p.capture&&p.spatial(s));
            var a=path.entry.a();var delta=path.entry.at(.3).subtract(a);var offset=point(s).subtract(a);
            double t=Math.clamp((offset.x()*delta.x()+offset.y()*delta.y()+offset.z()*delta.z())/Math.max(.001,delta.length()*delta.length()),0,1);
            return offset.subtract(delta.scale(t)).length()<=radius;
        }
        public boolean dynamic(FlightPrediction.State s,int remaining) {
            if(!sections.isEmpty())return (rocket==0?remaining==0:remaining>0&&remaining<=12)&&s.rocketDelay()==0
                &&sections.stream().anyMatch(p->p.dynamic(s));
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
    public record Capture(Window window,Section section,int duration,int igniteIn,int captureTicks,double cost,
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
        // Explicit non-horizontal strike tangents, including sustained turning while
        // diving/climbing. Geometry is sampled symmetrically; only physics may reject it.
        for(int heading=0;heading<8;heading++)for(double slope:new double[]{-.8,-.4,.4,.8})for(double bend:new double[]{0,-.3,.3}){
            double yaw=Math.toRadians(heading*45);var f=new Point(-Math.sin(yaw),0,Math.cos(yaw));
            var side=new Point(f.z(),0,-f.x());var strike=in.target;double length=30;
            var tangent=f.add(new Point(0,slope,0)).unit();
            var a=strike.subtract(f.scale(length)).add(side.scale(bend*length*.5)).add(new Point(0,-slope*length,0));
            var b=strike.add(f.scale(24)).add(side.scale(bend*10)).add(new Point(0,Math.max(3,slope*18),0));
            var entryT=f.add(side.scale(bend)).add(new Point(0,slope,0)).unit();
            var exitT=f.add(side.scale(bend*.5)).add(new Point(0,.3,0)).unit();
            paths.add(new Path(new Cubic(a,a.add(entryT.scale(17)),strike.subtract(tangent.scale(7)),strike),
                new Cubic(strike,strike.add(tangent.scale(7)),b.subtract(exitT.scale(13)),b),
                slope<0?"FUNNEL_DIVE_ARC":"FUNNEL_ASCENT_ARC",bend==0?0:.3));
        }
        return List.copyOf(paths);
    }
    public static FlightPrediction.State witness(Path path,double speed,PhysicsContext physics) {
        var a=path.entry.a();var tangent=path.entry.b().subtract(a).unit();
        double horizontal=Math.hypot(tangent.x(),tangent.z());
        return new FlightPrediction.State(a.x(),a.y(),a.z(),tangent.x()/horizontal*speed,tangent.y()/horizontal*speed-.05,tangent.z()/horizontal*speed,
            Math.toDegrees(Math.atan2(-tangent.x(),tangent.z())),-Math.toDegrees(Math.atan2(tangent.y(),horizontal)),physics);
    }
    public static Atlas discover(Input in,GlidePlanner.Corridor space,Contact contact) {
        var windows=new ArrayList<Window>();int id=0;
        // Fixed 256 geometries, two speeds and two rocket states. No maid-pose search.
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
        // Reserve representation for feasible vertical families. Pure cost ordering used
        // to fill all four slots with different azimuths of the same horizontal pass.
        var ordered=new ArrayList<Window>();
        if(!windows.isEmpty())ordered.add(windows.getFirst());
        for(String family:new String[]{"FUNNEL_DIVE_ARC","FUNNEL_ASCENT_ARC"})
            windows.stream().filter(w->w.path.style.equals(family)).findFirst().ifPresent(ordered::add);
        ordered.addAll(windows);
        // Keep different entry directions instead of four nearly identical speed/height samples.
        var diverse=new ArrayList<Window>();
        for(var w:ordered) {
            if(diverse.stream().anyMatch(old->angle(old.witness.yaw(),w.witness.yaw())<25&&Math.abs(old.witness.pitch()-w.witness.pitch())<18))continue;
            diverse.add(envelope(w,in,space,contact));
            if(diverse.size()==MAX_WINDOWS)break;
        }
        return new Atlas(diverse);
    }
    private static Window envelope(Window w,Input in,GlidePlanner.Corridor space,Contact contact){
        var seeds=new ArrayList<FlightPrediction.State>();var rolls=new ArrayList<Rollout>();
        var e=w.witness;double yaw=Math.toRadians(e.yaw());
        // 27 nearby starts vary location and momentum/attitude; only successful complete
        // Strike + Exit demonstrations contribute to a section's actual state ranges.
        for(double lateral:new double[]{0,-3,3})for(double vertical:new double[]{0,-2,2})for(double turn:new double[]{0,-10,10}){
            double a=Math.toRadians(e.yaw()+turn),speed=Math.hypot(e.vx(),e.vz())*(turn==0?1:turn<0?.9:1.1);
            var s=new FlightPrediction.State(e.x()+Math.cos(yaw)*lateral,e.y()+vertical,e.z()+Math.sin(yaw)*lateral,
                -Math.sin(a)*speed,e.vy()+vertical*.04,Math.cos(a)*speed,e.yaw()+turn,e.pitch()+turn*.4,e.physics());
            var roll=simulate(s,w.path,in.aim,w.rocket,in.physics.gravity(),in.rate,in.minimum,false,space,contact,120);
            if(roll.safe()&&roll.hit()){seeds.add(s);rolls.add(roll);}
        }
        var proof=simulate(e,w.path,in.aim,w.rocket,in.physics.gravity(),in.rate,in.minimum,false,space,contact,120);
        if(seeds.isEmpty()){seeds.add(e);rolls.add(proof);}
        var sections=new ArrayList<Section>();var centers=new ArrayList<Point>();
        // Match demonstrations by normalized entry/exit time, keeping strike at one station.
        for(int station=0;station<17;station++){
            var states=new ArrayList<FlightPrediction.State>();
            for(var roll:rolls){
                int index=station<=8?(int)Math.round(roll.contactTick()*station/8.0):
                    roll.contactTick()+(int)Math.round((roll.trace().size()-1-roll.contactTick())*(station-8)/8.0);
                states.add(roll.trace().get(Math.clamp(index,0,roll.trace().size()-1)).state());
            }
            double x=0,y=0,z=0;for(var s:states){x+=s.x();y+=s.y();z+=s.z();}
            var center=new Point(x/states.size(),y/states.size(),z/states.size());var ref=states.getFirst();
            double width=.35,height=.25,heading=8,pitch=6,minVy=ref.vy(),maxVy=ref.vy(),minSpeed=99,maxSpeed=0;
            for(var s:states){width=Math.max(width,Math.hypot(s.x()-center.x(),s.z()-center.z()));height=Math.max(height,Math.abs(s.y()-center.y()));
                heading=Math.max(heading,angle(s.yaw(),ref.yaw()));pitch=Math.max(pitch,Math.abs(s.pitch()-ref.pitch()));
                minVy=Math.min(minVy,s.vy());maxVy=Math.max(maxVy,s.vy());double speed=Math.hypot(s.vx(),s.vz());minSpeed=Math.min(minSpeed,speed);maxSpeed=Math.max(maxSpeed,speed);}
            sections.add(new Section(center,Math.min(6,width+.6),Math.min(4,height+.5),ref.yaw(),ref.pitch(),Math.min(40,heading+25),Math.min(35,pitch+25),
                minVy-.4,maxVy+.4,Math.max(in.minimum,minSpeed-.4),maxSpeed+.45,station<=5));centers.add(center);
        }
        return new Window(w.id,w.path,e,w.rocket,w.radius,w.minimumSpeed,w.maximumSpeed,w.headingTolerance,w.pitchTolerance,w.verticalTolerance,proof,centers,sections,seeds);
    }
    private static List<Point> tube(Path path) {
        var out=new ArrayList<Point>();var points=path.points();
        int stride=Math.max(1,(int)Math.ceil((points.size()-1)/16.0));
        for(int i=0;i<points.size()-1;i+=stride)out.add(points.get(i));
        out.add(points.getLast());return List.copyOf(out);
    }
    /** Cheap feedback also used by the main thread; all search and proof stays on workers. */
    public static Command captureCommand(FlightPrediction.State s,Window w,int rocket,double gravity,double rate) {
        return captureCommand(s,w,section(s,w),rocket,gravity,rate);
    }
    public static Command captureCommand(FlightPrediction.State s,Window w,Section section,int rocket,double gravity,double rate) {
        var entry=section.center;double radians=Math.toRadians(section.yaw);
        var tangent=new Point(-Math.sin(radians),0,Math.cos(radians));
        var offset=point(s).subtract(entry);double along=offset.x()*tangent.x()+offset.z()*tangent.z();
        double lateral=offset.x()*tangent.z()-offset.z()*tangent.x();
        // Acquire an upstream alignment point before crossing the entry plane from the side.
        var goal=entry.add(tangent.scale(Math.abs(lateral)>7||along>4?-10:5));
        double speed=Math.hypot(s.vx(),s.vz());
        double desiredVy=Math.clamp((entry.y()-s.y())*.08,-.7,.7);
        double pitch=GlidePlanner.feedbackPitch(s,entry.y(),desiredVy,gravity,rocket>0);
        double yaw=Math.toDegrees(Math.atan2(s.x()-goal.x(),goal.z()-s.z()));
        if(speed>w.maximumSpeed&&angle(s.yaw(),yaw)>35&&s.y()<entry.y()+3)pitch=Math.min(pitch,-30);
        if(rocket==0&&speed<.65)pitch=Math.max(pitch,-6);
        return new Command(yaw,Math.clamp(pitch,-55,45),rate,6);
    }
    public static Window nearest(FlightPrediction.State s,Atlas atlas) {
        return atlas.windows.stream().min(Comparator.comparingDouble(w->captureCost(s,section(s,w)))).orElse(null);
    }
    public static Section section(FlightPrediction.State s,Window w){
        return captureSections(w).stream().min(Comparator.comparingDouble(p->captureCost(s,p))).orElseThrow();
    }
    private static List<Section> captureSections(Window w){
        if(!w.sections.isEmpty())return w.sections.stream().filter(Section::capture).toList();
        return List.of(new Section(point(w.witness),w.radius,w.radius,w.witness.yaw(),w.witness.pitch(),w.headingTolerance,w.pitchTolerance,
            w.witness.vy()-w.verticalTolerance,w.witness.vy()+w.verticalTolerance,w.minimumSpeed,w.maximumSpeed,true));
    }
    public static double captureCost(FlightPrediction.State s,Section p){
        double speed=Math.hypot(s.vx(),s.vz()),velocityYaw=Math.toDegrees(Math.atan2(-s.vx(),s.vz()));
        return point(s).subtract(p.center).length()+angle(s.yaw(),p.yaw)*.12+angle(velocityYaw,p.yaw)*.08
            +Math.abs(s.pitch()-p.pitch)*.1+Math.abs(speed-(p.minimumSpeed+p.maximumSpeed)*.5)*8+Math.max(0,p.center.y()-s.y())*.5;
    }
    /** Reserve relative to the next capture plane, never the valley floor. Scheduling only. */
    public static double reserve(FlightPrediction.State s,Section p,double gravity){
        double speed2=s.vx()*s.vx()+s.vz()*s.vz();
        return speed2-p.minimumSpeed*p.minimumSpeed+2*Math.max(0,gravity)*(s.y()-p.center.y());
    }
    /** Coast first; only real inventory durations reach this function. Every lifetime must pass. */
    public static Capture capture(FlightPrediction.State start,Atlas atlas,FlightPrediction.Aim aim,int rocket,
                                  double rate,double minimum,List<Integer> durations,boolean immediateOnly,
                                  GlidePlanner.Corridor space,Contact contact) {
        Capture best=null;
        for(var w:atlas.windows)for(var section:options(start,w)) {
            var coast=trial(start,w,section,aim,rocket,rate,minimum,-1,-1,0,space,contact);
            if(coast!=null&&(best==null||coast.cost<best.cost))best=coast;
        }
        if(best!=null||rocket>0)return best;
        for(int duration:durations.stream().filter(d->d>=0&&RocketProfile.maximumTicks(d)<=80).distinct().sorted().toList()) {
            for(var w:atlas.windows)for(var section:options(start,w))for(int when:immediateOnly?new int[]{0}:new int[]{0,8,16}) {
                Capture candidate=null;double cost=0;boolean safe=true;
                for(int life=RocketProfile.minimumTicks(duration);life<=RocketProfile.maximumTicks(duration);life++) {
                    var result=trial(start,w,section,aim,0,rate,minimum,duration,when,life,space,contact);
                    if(result==null){safe=false;break;}
                    cost+=result.cost;candidate=result;
                }
                if(safe) {
                    var result=new Capture(w,section,duration,when,candidate.captureTicks,cost/12,candidate.first,candidate.trace);
                    if(best==null||result.cost<best.cost)best=result;
                }
            }
            if(best!=null)return best; // minimal sufficient inventory pulse, not fastest pulse
        }
        return null;
    }
    private static List<Section> options(FlightPrediction.State start,Window w){
        return captureSections(w).stream().sorted(Comparator.comparingDouble(s->captureCost(start,s))).limit(3).toList();
    }
    private static Capture trial(FlightPrediction.State start,Window w,Section section,FlightPrediction.Aim aim,int active,
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
                    return new Capture(w,section,duration,when,tick,tick+proof.cost(),first==null?proof.first():first,trace);
                }
            }
            if(tick==MAX_CAPTURE_TICKS)break;
            var c=captureCommand(s,w,section,remaining,gravity,rate);if(first==null)first=c;
            var next=FlightPrediction.step(s,c.yaw(),c.pitch(),c.yawRate(),c.pitchRate(),c.pitchRate(),gravity,remaining>0&&delay==0);
            if(!space.clear(s,next,.15,.12))return null;
            if(delay>0)delay--;else if(remaining>0)remaining--;
            s=next.withRocketDelay(delay);trace.add(new GlidePlanner.TracePoint(s,.15,.12));
        }
        return null;
    }
}
