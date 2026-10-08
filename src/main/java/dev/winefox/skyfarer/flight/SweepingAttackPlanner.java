package dev.winefox.skyfarer.flight;

import java.util.ArrayList;
import java.util.List;

/** Joined cubic attack/exit candidates. Only finite-pulse vanilla motion can certify a candidate. */
public final class SweepingAttackPlanner {
    private SweepingAttackPlanner() {}
    public record Point(double x,double y,double z) {
        public Point add(Point b){return new Point(x+b.x,y+b.y,z+b.z);}
        public Point subtract(Point b){return new Point(x-b.x,y-b.y,z-b.z);}
        public Point scale(double v){return new Point(x*v,y*v,z*v);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public Point unit(){return scale(1/Math.max(.0001,length()));}
    }
    public record Cubic(Point a,Point b,Point c,Point d) {
        public Point at(double t){double u=1-t,aa=u*u*u,bb=3*u*u*t,cc=3*u*t*t,dd=t*t*t;return new Point(a.x*aa+b.x*bb+c.x*cc+d.x*dd,a.y*aa+b.y*bb+c.y*cc+d.y*dd,a.z*aa+b.z*bb+c.z*cc+d.z*dd);}
    }
    public static final class Path {
        public final Cubic entry,exit;
        public final String style;
        public final double simplicity;
        public final boolean tracking;
        private final List<Point> points;
        private final double[] lengths;
        private final double[] remainingHeights;
        public final int contactIndex;
        public Path(Cubic entry,Cubic exit,String style,double simplicity){
            this(entry,exit,style,simplicity,false);
        }
        public Path(Cubic entry,Cubic exit,String style,double simplicity,boolean tracking){
            this.tracking=tracking;
            this.entry=entry;this.exit=exit;this.style=style;this.simplicity=simplicity;
            var p=new ArrayList<Point>();sample(entry,p);contactIndex=p.size()-1;
            var tail=new ArrayList<Point>();sample(exit,tail);p.addAll(tail.subList(1,tail.size()));points=List.copyOf(p);
            remainingHeights=new double[p.size()]; double peak=-Double.MAX_VALUE;
            for(int i=p.size()-1;i>=0;i--){peak=Math.max(peak,p.get(i).y);remainingHeights[i]=peak;}
            lengths=new double[p.size()];for(int i=1;i<p.size();i++)lengths[i]=lengths[i-1]+p.get(i).subtract(p.get(i-1)).length();
        }
        private static void sample(Cubic c,List<Point> out){
            int n=Math.clamp((int)Math.ceil((c.a.subtract(c.b).length()+c.b.subtract(c.c).length()+c.c.subtract(c.d).length())/.75),8,160);
            for(int i=0;i<=n;i++)out.add(c.at(i/(double)n));
        }
        public List<Point> points(){return points;}
        public double requiredHeight(Point p,boolean exiting){
            double along=nearest(p,exiting);int index=0;
            while(index+1<lengths.length&&lengths[index+1]<=along)index++;
            return remainingHeights[index];
        }
        public double length(){return lengths[lengths.length-1];}
        public double remaining(Point p,boolean exiting){return lengths[contactIndex]-nearest(p,exiting);}
        /** A turning entry may start beyond the final tangent plane without having reached contact. */
        public boolean passedContact(Point p,double margin){
            if(remaining(p,false)>margin)return false;
            var tangent=entry.d.subtract(entry.c).unit();var offset=p.subtract(entry.d);
            return offset.x*tangent.x+offset.z*tangent.z>margin;
        }
        private double nearest(Point p,boolean exiting){
            double best=Double.POSITIVE_INFINITY,along=exiting?lengths[contactIndex]:0;
            int lo=exiting?contactIndex:0,hi=exiting?points.size()-1:contactIndex;
            for(int i=lo;i<hi;i++){
                var a=points.get(i);var b=points.get(i+1);
                double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z,vx=p.x-a.x,vy=p.y-a.y,vz=p.z-a.z;
                double t=Math.clamp((vx*dx+vy*dy+vz*dz)/Math.max(.00001,dx*dx+dy*dy+dz*dz),0,1);
                double ex=vx-dx*t,ey=vy-dy*t,ez=vz-dz*t,distance=ex*ex+ey*ey+ez*ez;
                if(distance<best){best=distance;along=lengths[i]+t*(lengths[i+1]-lengths[i]);}
            }
            return along;
        }
        private Point atDistance(double distance){
            for(int i=1;i<points.size();i++)if(lengths[i]>=distance){double t=(distance-lengths[i-1])/Math.max(.001,lengths[i]-lengths[i-1]);return points.get(i-1).scale(1-t).add(points.get(i).scale(t));}
            return points.getLast();
        }
        public boolean clear(GlidePlanner.Corridor space,double side,double vertical){
            for(int i=1;i<points.size();i++)if(!space.clear(state(points.get(i-1)),state(points.get(i)),side,vertical))return false;
            return true;
        }
    }
    @FunctionalInterface public interface Contact { boolean clear(FlightPrediction.State state,FlightPrediction.Aim aim,int tick); }
    public record Command(double yaw,double pitch,double yawRate,double pitchRate){}
    public record Rollout(boolean safe,boolean hit,int contactTick,double cost,double miss,Command first,List<GlidePlanner.TracePoint> trace,EnergyReserveEnvelope.Result envelope){
        public Rollout(boolean safe,boolean hit,int contactTick,double cost,double miss,Command first,List<GlidePlanner.TracePoint> trace){
            this(safe,hit,contactTick,cost,miss,first,trace,new EnergyReserveEnvelope.Result(Double.NaN,-1,List.of()));
        }
    }
    public record Selection(Path path,Rollout prediction){}
    public record Search(Selection selection,int candidates,int geometryRejected,int contactRejected,int exitRejected,int accepted,double bestMiss){}
    /** Re-certify a stable entry after asynchronous latency; keep C1 and adjust only recovery headroom. */
    public static Selection rejoin(FlightPrediction.State start,Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,GlidePlanner.Corridor space,Contact contact){
        for(double rise:new double[]{0,1,2,4,6}){
            var lift=new Point(0,rise,0);
            var candidate=rise==0?path:new Path(path.entry,new Cubic(path.exit.a,path.exit.b,path.exit.c.add(lift),path.exit.d.add(lift)),path.style,path.simplicity+rise*.1,path.tracking);
            var prediction=simulate(start,candidate,aim,rocket,gravity,rate,minimum,false,space,contact,120);
            if(prediction.safe&&prediction.hit)return new Selection(candidate,prediction);
        }
        return null;
    }
    public static Point point(FlightPrediction.State s){return new Point(s.x(),s.y(),s.z());}
    /** Solve the horizontal rendezvous time, including the native use windup.
     * This only seeds geometry: the rollout still checks the moving body every tick. */
    public static double interceptTicks(FlightPrediction.State start,FlightPrediction.Aim aim){
        double dx=aim.x()-start.x(),dz=aim.z()-start.z();
        double speed=Math.max(.55,Math.hypot(start.vx(),start.vz()));
        double a=aim.vx()*aim.vx()+aim.vz()*aim.vz()-speed*speed;
        double b=2*(dx*aim.vx()+dz*aim.vz()),c=dx*dx+dz*dz;
        double t=Math.sqrt(c)/speed,disc=b*b-4*a*c;
        if(Math.abs(a)>1e-6&&disc>=0){
            double u=(-b-Math.sqrt(disc))/(2*a),v=(-b+Math.sqrt(disc))/(2*a);
            if(u>0)t=u;if(v>0)t=u>0?Math.min(u,v):v;
        }
        return Math.clamp(t,Math.min(60,aim.readyIn()+2),60);
    }
    public static List<Path> interceptCandidates(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,double exitLength){
        if(aim==null||Math.hypot(aim.vx(),aim.vz())<.025)return candidates(start,target,exitLength);
        var paths=new ArrayList<Path>();double estimate=interceptTicks(start,aim);
        for(double factor:new double[]{1,.65,1.35}){
            double lead=Math.clamp(estimate*factor,Math.min(72,aim.readyIn()),72);
            var strike=target.add(new Point(aim.vx()*lead,aim.vy()*lead,aim.vz()*lead));
            for(var path:candidates(start,strike,exitLength))paths.add(new Path(path.entry,path.exit,path.style,path.simplicity,true));
        }
        return paths;
    }
    private static FlightPrediction.State state(Point p){return new FlightPrediction.State(p.x,p.y,p.z,0,0,0,0,0);}
    public static List<Path> candidates(FlightPrediction.State start,Point target,double exitLength){
        var paths=new ArrayList<Path>();var origin=point(start);var delta=target.subtract(origin);double distance=delta.length();
        var velocity=new Point(start.vx(),start.vy(),start.vz());
        var tangent=velocity.length()>.15?velocity.unit():delta.unit();
        double direct=Math.atan2(delta.x,delta.z),horizontal=Math.hypot(delta.x,delta.z);
        for(double angle:new double[]{0,-30,30,-60,60,-90,90,-120,120})for(double scale:new double[]{.55,.9})for(double lift:new double[]{0,1.2})for(double offset:new double[]{0,-1.2,1.2}){
            double a=direct+Math.toRadians(angle),sign=angle==0?0:Math.signum(angle);
            double slope=Math.clamp(delta.y/Math.max(8,horizontal),-.65,.65);
            var direction=new Point(Math.sin(a),slope*.35,Math.cos(a)).unit();
            var contact=target.add(new Point(direction.z*offset,lift,-direction.x*offset));
            double handle=Math.clamp(distance*scale,5,36),join=Math.clamp(distance*.3,4,exitLength*.65);
            var entry=new Cubic(origin,origin.add(tangent.scale(handle)),contact.subtract(direction.scale(join)),contact);
            var endDirection=new Point(Math.sin(a+sign*.65),.25,Math.cos(a+sign*.65)).unit();
            var end=contact.add(direction.scale(exitLength*.65)).add(endDirection.scale(exitLength*.65)).add(new Point(0,2,0));
            var exit=new Cubic(contact,contact.add(direction.scale(join)),end.subtract(endDirection.scale(exitLength*.45)),end);
            paths.add(new Path(entry,exit,angle==0?"DIRECT":Math.abs(angle)<90?(angle<0?"LEFT_ARC":"RIGHT_ARC"):(angle<0?"LEFT_SWEEP":"RIGHT_SWEEP"),angle==0?0:1.5));
        }
        // Add a bounded, symmetric vertical family. Keep the original 108 shallow candidates.
        // A rising initial tangent plus a descending strike naturally crests; the inverse dips.
        for(double angle:new double[]{0,-30,30,-60,60,-90,90,-120,120})
            for(double elevation:new double[]{-70,-60,-50,50,60,70})for(double scale:new double[]{.55,.9}) {
                double a=direct+Math.toRadians(angle),e=Math.toRadians(elevation),sign=Math.signum(angle);
                var direction=new Point(Math.sin(a)*Math.cos(e),Math.sin(e),Math.cos(a)*Math.cos(e));
                double handle=Math.clamp(distance*scale,5,36),join=Math.clamp(distance*.18,2.5,5);
                var entry=new Cubic(origin,origin.add(tangent.scale(handle)),target.subtract(direction.scale(join)),target);
                // The exit can flatten and turn immediately after the C1 join; physics certifies the pullout.
                var endDirection=new Point(Math.sin(a+sign*.85),.2,Math.cos(a+sign*.85)).unit();
                var end=target.add(endDirection.scale(exitLength)).add(new Point(0,2,0));
                var exit=new Cubic(target,target.add(direction.scale(join)),end.subtract(endDirection.scale(exitLength*.45)),end);
                paths.add(new Path(entry,exit,"VERTICAL_"+(elevation<0?"DIVE":"CLIMB")+(angle==0?"":angle<0?"_LEFT":"_RIGHT"),angle==0?0:1.5));
            }
        return paths;
    }
    public static Command command(FlightPrediction.State s,Path path,FlightPrediction.Aim aim,boolean exiting,int rocket,double gravity,double rate){
        Point here=point(s);double along=path.nearest(here,exiting),speed=path.style.startsWith("VERTICAL_")
            ?Math.sqrt(s.vx()*s.vx()+s.vy()*s.vy()+s.vz()*s.vz()):Math.hypot(s.vx(),s.vz());
        Point goal=path.atDistance(along+Math.clamp(speed*7,3,10));
        // Track the moving body, not just its look direction: compensate for elytra turn lag.
        var tangent=path.atDistance(along+1).subtract(path.atDistance(Math.max(0,along-1))).unit();
        double dot=s.vx()*tangent.x+s.vz()*tangent.z;
        double dx=goal.x-s.x()-(s.vx()-dot*tangent.x)*3,dz=goal.z-s.z()-(s.vz()-dot*tangent.z)*3;
        // Terminal interception follows the latest body motion. A fixed Bezier endpoint is
        // only an entry proposal; carrying it unchanged through a reversal creates a miss.
        if(!exiting&&aim!=null&&path.tracking&&path.remaining(here,false)<Math.max(12,speed*12)){
            double distance=Math.hypot(aim.x()-s.x(),aim.z()-s.z());
            double closing=Math.max(.35,speed-((aim.x()-s.x())*aim.vx()+(aim.z()-s.z())*aim.vz())/Math.max(.1,distance));
            double lead=Math.clamp((distance-aim.maxReach()*.5)/closing,0,12);
            dx=aim.x()+aim.vx()*lead-s.x();dz=aim.z()+aim.vz()*lead-s.z();
        }
        if(!exiting&&aim!=null&&Math.hypot(aim.x()-s.x(),aim.z()-s.z())<aim.maxReach()+.75){dx=aim.x()-s.x();dz=aim.z()-s.z();}
        double yaw=Math.toDegrees(Math.atan2(-dx,dz));
        double vy=tangent.y*speed;
        double pitch=GlidePlanner.feedbackPitch(s,goal.y,vy,gravity,rocket>0);
        if(path.style.startsWith("VERTICAL_")) {
            double horizontal=Math.max(.2,Math.hypot(goal.x-s.x(),goal.z-s.z()));
            pitch=-Math.toDegrees(Math.atan2(goal.y-s.y()+(vy-s.vy())*2,horizontal));
            if(!exiting&&aim!=null&&Math.hypot(aim.x()-s.x(),aim.z()-s.z())<aim.maxReach()+.75){
                double center=aim.bottom()+aim.height()*.5;
                pitch=-Math.toDegrees(Math.atan2(center-s.y(),Math.max(.05,Math.hypot(aim.x()-s.x(),aim.z()-s.z()))));
            }
        }
        if(!exiting&&aim!=null&&path.tracking&&Math.hypot(aim.x()-s.x(),aim.z()-s.z())<aim.maxReach()+speed*3){
            pitch=-Math.toDegrees(Math.atan2(aim.bottom()+aim.height()*.5-s.y(),Math.max(.05,Math.hypot(dx,dz))));
        }
        return new Command(yaw,Math.clamp(pitch,-75,75),rate,exiting&&path.style.startsWith("VERTICAL_")?14:6);
    }
    public static Rollout simulate(FlightPrediction.State start,Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minSpeed,boolean exiting,GlidePlanner.Corridor space,Contact contact,int budget){
        return simulate(start,path,aim,rocket,gravity,rate,minSpeed,exiting,space,contact,budget,true);
    }
    private static Rollout simulate(FlightPrediction.State start,Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minSpeed,boolean exiting,GlidePlanner.Corridor space,Contact contact,int budget,boolean recordTrace){
        var s=start;boolean hit=exiting;int hitTick=exiting?0:-1;double miss=Double.POSITIVE_INFINITY,cost=path.simplicity;Command first=null;
        var trace=new ArrayList<GlidePlanner.TracePoint>();if(recordTrace)trace.add(new GlidePlanner.TracePoint(s,.15,.12));
        int horizon=Math.min(160,Math.max(budget,rocket+start.rocketDelay()+16));
        var envelope=new EnergyReserveEnvelope(recordTrace);double exitTravel=0;
        envelope.observe(start,0,exiting?EnergyReserveEnvelope.Stage.EXIT:EnergyReserveEnvelope.Stage.APPROACH,
            path.requiredHeight(point(start),exiting),exiting?Math.max(.3,minSpeed):minSpeed,gravity);
        double exitLength=exiting?Math.max(0,path.length()-path.nearest(point(start),true)):path.exit.d.subtract(path.exit.a).length();
        // Entry rollouts extend the horizon when contact occurs. A follow-up EXIT rollout
        // starts after contact and must receive the same full remaining-travel budget.
        // Otherwise a safe slow exit is mislabeled unsafe solely at the 24-tick deadline.
        if(exiting)horizon=Math.min(160,Math.max(horizon,17+(int)Math.ceil(exitLength/.3)));
        var outward=path.exit.d.subtract(path.exit.c);
        double outwardYaw=Math.toDegrees(Math.atan2(-outward.x,outward.z));
        for(int tick=0;tick<horizon;tick++){
            var moving=aim==null?null:aim.after(tick);
            // ATTACK_EXIT ends after its bounded travel, even if the rocket still burns.
            // Certify the remaining pulse/coast along the outgoing heading instead of
            // orbiting an already completed Bezier endpoint that the controller leaves.
            var c=hit&&exitTravel>=exitLength
                ?new Command(outwardYaw,GlidePlanner.feedbackPitch(s,path.exit.d.y,0,gravity,RocketProfile.active(tick,rocket,start)),rate,6)
                :command(s,path,moving,hit,RocketProfile.remaining(tick,rocket,start),gravity,rate);
            if(first==null)first=c;
            var look=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),c.yaw,c.yawRate),FlightPrediction.turn(s.pitch(),c.pitch,c.pitchRate));
            if(!hit&&tick>=aim.readyIn()&&Math.hypot(s.vx(),s.vz())>=minSpeed){
                double error=aim.missSquared(look,tick);miss=Math.min(miss,error);
                if(error<=1e-8&&contact.clear(look,aim,tick)){
                    hit=true;hitTick=tick;
                    // Contact near the search deadline still needs its whole recovery.
                    horizon=Math.min(160,Math.max(horizon,hitTick+17+(int)Math.ceil(exitLength/.3)));
                }
            }
            var next=FlightPrediction.step(s,c.yaw,c.pitch,c.yawRate,c.pitchRate,c.pitchRate,gravity,RocketProfile.active(tick,rocket,start));
            if(!space.clear(s,next,.15,.12))return new Rollout(false,hit,hitTick,Double.POSITIVE_INFINITY,miss,first,List.copyOf(trace));
            boolean roomy=space.clear(s,next,.5,.4);
            cost+=.35+(roomy?0:.8)+.018*Math.pow(FlightPrediction.turn(s.yaw(),c.yaw,c.yawRate)-s.yaw(),2);
            if(hit)exitTravel+=point(next).subtract(point(s)).length();
            envelope.observe(next,tick+1,hitTick==tick?EnergyReserveEnvelope.Stage.STRIKE:hit?EnergyReserveEnvelope.Stage.EXIT:EnergyReserveEnvelope.Stage.APPROACH,
                path.requiredHeight(point(next),hit),hit?Math.max(.3,minSpeed):minSpeed,gravity);
            s=next;if(recordTrace)trace.add(new GlidePlanner.TracePoint(s,.15,.12));
            if(hit&&tick>=Math.max(rocket+start.rocketDelay()+15,hitTick+16)
                &&exitTravel>=exitLength
                &&Math.hypot(s.vx(),s.vz())>=Math.max(.3,minSpeed)) {
                var reserve=envelope.result();
                return new Rollout(true,true,hitTick,cost+Math.max(0,-reserve.worstReserve())*2,miss,first,List.copyOf(trace),reserve);
            }
        }
        return new Rollout(false,hit,hitTick,Double.POSITIVE_INFINITY,miss,first,List.copyOf(trace),envelope.result());
    }
    public static Selection choose(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minimumSpeed,double exitLength,GlidePlanner.Corridor space,Contact contact){
        return choose(start,target,aim,rocket,gravity,rate,minimumSpeed,exitLength,space,contact,0);
    }
    public static Selection choose(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minimumSpeed,double exitLength,GlidePlanner.Corridor space,Contact contact,double currentTurn){
        return search(start,target,aim,rocket,gravity,rate,minimumSpeed,exitLength,space,contact,currentTurn).selection();
    }
    public static Search search(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minimumSpeed,double exitLength,GlidePlanner.Corridor space,Contact contact,double currentTurn){
        Selection best=null;
        int count=0,geometryRejected=0,contactRejected=0,exitRejected=0,accepted=0;double miss=Double.POSITIVE_INFINITY;
        for(var path:interceptCandidates(start,target,aim,exitLength)){
            count++;
            if(!path.clear(space,.15,.12)){geometryRejected++;continue;}
            var result=simulate(start,path,aim,rocket,gravity,rate,minimumSpeed,false,space,contact,120,false);
            miss=Math.min(miss,result.miss);
            if(!result.hit)contactRejected++;else if(!result.safe)exitRejected++;else accepted++;
            double turn=FlightPrediction.turn(start.yaw(),result.first.yaw,rate)-start.yaw();
            if(turn*currentTurn<-.1)result=new Rollout(result.safe,result.hit,result.contactTick,result.cost+Math.min(3,Math.abs(currentTurn))*.6,result.miss,result.first,result.trace,result.envelope);
            if(result.safe&&result.hit&&(best==null||result.cost<best.prediction.cost))best=new Selection(path,result);
        }
        if(best==null)return new Search(null,count,geometryRejected,contactRejected,exitRejected,accepted,miss);
        var traced=simulate(start,best.path,aim,rocket,gravity,rate,minimumSpeed,false,space,contact,120);
        var p=best.prediction;
        return new Search(new Selection(best.path,new Rollout(p.safe,p.hit,p.contactTick,p.cost,p.miss,p.first,traced.trace,traced.envelope)),count,geometryRejected,contactRejected,exitRejected,accepted,miss);
    }
}
