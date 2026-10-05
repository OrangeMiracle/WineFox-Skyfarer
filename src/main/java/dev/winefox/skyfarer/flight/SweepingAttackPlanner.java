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
        public Point at(double t){double u=1-t;return a.scale(u*u*u).add(b.scale(3*u*u*t)).add(c.scale(3*u*t*t)).add(d.scale(t*t*t));}
    }
    public static final class Path {
        public final Cubic entry,exit;
        public final String style;
        public final double simplicity;
        private final List<Point> points;
        private final double[] lengths;
        public final int contactIndex;
        public Path(Cubic entry,Cubic exit,String style,double simplicity){
            this.entry=entry;this.exit=exit;this.style=style;this.simplicity=simplicity;
            var p=new ArrayList<Point>();sample(entry,p);contactIndex=p.size()-1;
            var tail=new ArrayList<Point>();sample(exit,tail);p.addAll(tail.subList(1,tail.size()));points=List.copyOf(p);
            lengths=new double[p.size()];for(int i=1;i<p.size();i++)lengths[i]=lengths[i-1]+p.get(i).subtract(p.get(i-1)).length();
        }
        private static void sample(Cubic c,List<Point> out){
            int n=Math.clamp((int)Math.ceil((c.a.subtract(c.b).length()+c.b.subtract(c.c).length()+c.c.subtract(c.d).length())/.75),8,160);
            for(int i=0;i<=n;i++)out.add(c.at(i/(double)n));
        }
        public List<Point> points(){return points;}
        public double length(){return lengths[lengths.length-1];}
        public double remaining(Point p,boolean exiting){return lengths[contactIndex]-nearest(p,exiting);}
        private double nearest(Point p,boolean exiting){
            double best=Double.POSITIVE_INFINITY,along=exiting?lengths[contactIndex]:0;
            int lo=exiting?contactIndex:0,hi=exiting?points.size()-1:contactIndex;
            for(int i=lo;i<hi;i++){
                var a=points.get(i);var d=points.get(i+1).subtract(a);var v=p.subtract(a);
                double t=Math.clamp((v.x*d.x+v.y*d.y+v.z*d.z)/Math.max(.00001,d.length()*d.length()),0,1);
                double distance=p.subtract(a.add(d.scale(t))).length();
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
    public record Rollout(boolean safe,boolean hit,int contactTick,double cost,double miss,Command first,List<GlidePlanner.TracePoint> trace){}
    public record Selection(Path path,Rollout prediction){}
    public static Point point(FlightPrediction.State s){return new Point(s.x(),s.y(),s.z());}
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
        return paths;
    }
    public static Command command(FlightPrediction.State s,Path path,FlightPrediction.Aim aim,boolean exiting,int rocket,double gravity,double rate){
        Point here=point(s);double along=path.nearest(here,exiting),speed=Math.hypot(s.vx(),s.vz());
        Point goal=path.atDistance(along+Math.clamp(speed*7,3,10));
        // Track the moving body, not just its look direction: compensate for elytra turn lag.
        var tangent=path.atDistance(along+1).subtract(path.atDistance(Math.max(0,along-1))).unit();
        double dot=s.vx()*tangent.x+s.vz()*tangent.z;
        double dx=goal.x-s.x()-(s.vx()-dot*tangent.x)*3,dz=goal.z-s.z()-(s.vz()-dot*tangent.z)*3;
        if(!exiting&&aim!=null&&Math.hypot(aim.x()-s.x(),aim.z()-s.z())<aim.maxReach()+.75){dx=aim.x()-s.x();dz=aim.z()-s.z();}
        double yaw=Math.toDegrees(Math.atan2(-dx,dz));
        double vy=tangent.y*speed;
        double pitch=GlidePlanner.feedbackPitch(s,goal.y,vy,gravity,rocket>0);
        return new Command(yaw,Math.clamp(pitch,-65,55),rate,6);
    }
    public static Rollout simulate(FlightPrediction.State start,Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minSpeed,boolean exiting,GlidePlanner.Corridor space,Contact contact,int budget){
        var s=start;boolean hit=exiting;int hitTick=exiting?0:-1;double miss=Double.POSITIVE_INFINITY,cost=path.simplicity;Command first=null;
        var trace=new ArrayList<GlidePlanner.TracePoint>();trace.add(new GlidePlanner.TracePoint(s,.15,.12));
        int horizon=Math.min(160,Math.max(budget,rocket+16));
        for(int tick=0;tick<horizon;tick++){
            var moving=aim==null?null:aim.after(tick);
            var c=command(s,path,moving,hit,Math.max(0,rocket-tick),gravity,rate);if(first==null)first=c;
            var look=new FlightPrediction.State(s.x(),s.y(),s.z(),s.vx(),s.vy(),s.vz(),FlightPrediction.turn(s.yaw(),c.yaw,c.yawRate),FlightPrediction.turn(s.pitch(),c.pitch,c.pitchRate));
            if(!hit&&tick>=aim.readyIn()&&Math.hypot(s.vx(),s.vz())>=minSpeed){
                double error=aim.missSquared(look,tick);miss=Math.min(miss,error);
                if(error<=1e-8&&contact.clear(look,aim,tick)){hit=true;hitTick=tick;}
            }
            var next=FlightPrediction.step(s,c.yaw,c.pitch,c.yawRate,c.pitchRate,c.pitchRate,gravity,tick<rocket);
            if(!space.clear(s,next,.15,.12))return new Rollout(false,hit,hitTick,Double.POSITIVE_INFINITY,miss,first,List.copyOf(trace));
            boolean roomy=space.clear(s,next,.5,.4);
            cost+=.35+(roomy?0:.8)+.018*Math.pow(FlightPrediction.turn(s.yaw(),c.yaw,c.yawRate)-s.yaw(),2);
            s=next;trace.add(new GlidePlanner.TracePoint(s,.15,.12));
            if(hit&&tick>=Math.max(rocket+12,hitTick+16))return new Rollout(true,true,hitTick,cost,miss,first,List.copyOf(trace));
        }
        return new Rollout(true,hit,hitTick,cost+(hit?0:100+Math.min(100,miss)*10),miss,first,List.copyOf(trace));
    }
    public static Selection choose(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minimumSpeed,double exitLength,GlidePlanner.Corridor space,Contact contact){
        return choose(start,target,aim,rocket,gravity,rate,minimumSpeed,exitLength,space,contact,0);
    }
    public static Selection choose(FlightPrediction.State start,Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,
        double minimumSpeed,double exitLength,GlidePlanner.Corridor space,Contact contact,double currentTurn){
        Selection best=null;
        for(var path:candidates(start,target,exitLength)){
            if(!path.clear(space,.15,.12))continue;
            var result=simulate(start,path,aim,rocket,gravity,rate,minimumSpeed,false,space,contact,120);
            double turn=FlightPrediction.turn(start.yaw(),result.first.yaw,rate)-start.yaw();
            if(turn*currentTurn<-.1)result=new Rollout(result.safe,result.hit,result.contactTick,result.cost+Math.min(3,Math.abs(currentTurn))*.6,result.miss,result.first,result.trace);
            if(result.safe&&result.hit&&(best==null||result.cost<best.prediction.cost))best=new Selection(path,result);
        }
        return best;
    }
}
