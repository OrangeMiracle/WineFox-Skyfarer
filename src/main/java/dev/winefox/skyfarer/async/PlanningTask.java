package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** Closed DTO vocabulary: no supplier or caller-provided callback can enter the executor. */
public sealed interface PlanningTask extends PlannerExecutor.Task<Object> {
    FlightPrediction.State start();
    default PlanningTask unbound(){return this;}
    record Bounded(PlanningTask task,CollisionSnapshot.Bounds bounds) implements PlanningTask {
        public FlightPrediction.State start(){return task.start();}
        public Slot slot(){return task.slot();}
        public PlanningTask unbound(){return task;}
        public Object compute(CollisionSnapshot.Space space){space.navigation(bounds);return task.compute(space);}
        public boolean compatible(PlanningTask old){return old instanceof Bounded b&&task.compatible(b.task)
            &&near(bounds.x(),b.bounds.x(),2)&&near(bounds.z(),b.bounds.z(),2)&&near(bounds.radius(),b.bounds.radius(),2);}
    }
    record Slot(Class<?> type,int variant,long option) {}
    default Slot slot(){
        if(this instanceof Pulse p)return new Slot(getClass(),p.duration,p.request.exit()==null?Long.MIN_VALUE:CollisionSnapshot.key((int)Math.floor(p.request.exit().x()),(int)Math.floor(p.request.exit().y()),(int)Math.floor(p.request.exit().z())));
        if(this instanceof Launch l)return new Slot(getClass(),l.duration,(l.plan==null?0:2)+(l.remaining?1:0));
        if(this instanceof Compact c)return new Slot(getClass(),c.duration,0);
        if(this instanceof Sweep s)return new Slot(getClass(),s.path==null?-1:s.budget,s.ignition?2+s.rocket:s.rocket==0?0:1);
        if(this instanceof Chain c)return new Slot(getClass(),c.primary==null?0:1,c.aim==null?0:CollisionSnapshot.key((int)Math.floor(c.aim.x()),(int)Math.floor(c.aim.bottom()),(int)Math.floor(c.aim.z())));
        return new Slot(getClass(),0,0);
    }
    default boolean compatible(PlanningTask old){return equals(old);}
    static boolean near(double a,double b,double tolerance){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b)||Math.abs(a-b)<=tolerance;}
    static boolean rocket(int a,int b,int tolerance){return (a==0)==(b==0)&&near(a,b,tolerance);}
    static boolean aim(FlightPrediction.Aim a,FlightPrediction.Aim b){return a==b||a!=null&&b!=null&&near(a.x(),b.x(),1)&&near(a.z(),b.z(),1)&&near(a.bottom(),b.bottom(),1)
        &&a.height()==b.height()&&a.halfWidth()==b.halfWidth()&&a.maxReach()==b.maxReach()&&a.minReach()==b.minReach()&&a.margin()==b.margin()
        &&near(a.vx(),b.vx(),.2)&&near(a.vy(),b.vy(),.2)&&near(a.vz(),b.vz(),.2)&&near(a.readyIn(),b.readyIn(),4);}
    static boolean request(GlidePlanner.Request a,GlidePlanner.Request b){return a.recovery()==b.recovery()&&a.flare()==b.flare()&&near(a.yaw(),b.yaw(),12)&&near(a.height(),b.height(),2)
        &&near(a.slope(),b.slope(),.15)&&rocket(a.rocketTicks(),b.rocketTicks(),4)&&aim(a.aim(),b.aim())&&exit(a.exit(),b.exit())
        &&a.gravity()==b.gravity()&&a.sideMargin()==b.sideMargin()&&a.verticalMargin()==b.verticalMargin()&&a.yawRate()==b.yawRate()&&a.pitchRate()==b.pitchRate()&&a.recoveryRate()==b.recoveryRate()&&near(a.floor(),b.floor(),0);}
    static boolean exit(GlidePlanner.Exit a,GlidePlanner.Exit b){return a==b||a!=null&&b!=null&&near(a.x(),b.x(),2)&&near(a.y(),b.y(),2)&&near(a.z(),b.z(),2)&&near(a.gradientX(),b.gradientX(),.1)&&near(a.gradientZ(),b.gradientZ(),.1);}
    record GlideResult(GlidePlanner.Plan plan,GlidePlanner.Forecast forecast,List<GlidePlanner.TracePoint> trace,double narrowCost){}
    record PulseResult(GlidePlanner.PulseReview review,java.util.Map<Integer,GlidePlanner.Plan> plans){
        public PulseResult {plans=java.util.Map.copyOf(plans);}
    }
    record Lane(AttackLanePlanner.Input input) implements PlanningTask {
        public FlightPrediction.State start(){return input.start();}
        public Object compute(CollisionSnapshot.Space s){return AttackLanePlanner.choose(input,s::segment);}
        public boolean compatible(PlanningTask p){return p instanceof Lane l&&near(input.x(),l.input.x(),1)&&near(input.y(),l.input.y(),.5)&&near(input.z(),l.input.z(),1)
            &&near(input.dx(),l.input.dx(),.12)&&near(input.dz(),l.input.dz(),.12)&&input.slopes().equals(l.input.slopes())&&input.fuel()==l.input.fuel();}
    }
    record Glide(FlightPrediction.State start,GlidePlanner.Request request) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){
            var p=GlidePlanner.choose(start,request,s);var forecast=GlidePlanner.forecast(start,request,p,s,0);var trace=GlidePlanner.trace(start,request,p,s);
            double cost=0;for(var point:trace)if(!s.clear(point.state(),point.state(),.5,.4))cost+=.8;
            return new GlideResult(p,forecast,trace,cost);
        }
        public boolean compatible(PlanningTask p){return p instanceof Glide g&&PlanningTask.request(request,g.request);}
    }
    /** Full Approach -> Strike -> Exit certificate, separate from a short steering hint. */
    record AttackCertificate(FlightPrediction.State start,GlidePlanner.Request request,double minimum) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){
            var plan=GlidePlanner.choose(start,request,s);
            if(!GlidePlanner.ordinary(plan))return new GlidePlanner.Forecast(false,Double.NEGATIVE_INFINITY);
            return GlidePlanner.forecast(start,request,plan,s,minimum,(state,aim,tick)->s.ray(state.x(),state.y(),state.z(),
                aim.x()+aim.vx()*tick,aim.bottom()+aim.height()*.5+aim.vy()*tick,aim.z()+aim.vz()*tick));
        }
        public boolean compatible(PlanningTask p){return p instanceof AttackCertificate a&&minimum==a.minimum&&PlanningTask.request(request,a.request);}
    }
    record Pulse(FlightPrediction.State start,GlidePlanner.Request request,int duration,double minimum,double reserve) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){var plans=new java.util.HashMap<Integer,GlidePlanner.Plan>();var review=GlidePlanner.reviewPulse(start,request,s,duration,minimum,reserve,plans);return new PulseResult(review,plans);}
        public boolean compatible(PlanningTask p){return p instanceof Pulse g&&duration==g.duration&&minimum==g.minimum&&reserve==g.reserve&&PlanningTask.request(request,g.request);}
    }
    record Compact(FlightPrediction.State start,CompactTurnPlanner.Goal goal,int rocket,double gravity,double rate,int duration) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){return duration<0?CompactTurnPlanner.choose(start,goal,rocket,gravity,rate,s):CompactTurnPlanner.pulseCost(start,goal,duration,gravity,rate,s);}
        public boolean compatible(PlanningTask p){return p instanceof Compact g&&duration==g.duration
            &&near(goal.x(),g.goal.x(),1)&&near(goal.y(),g.goal.y(),1)&&near(goal.z(),g.goal.z(),1)
            &&near(goal.originX(),g.goal.originX(),2)&&near(goal.originZ(),g.goal.originZ(),2)&&near(goal.radius(),g.goal.radius(),2)
            &&near(goal.nextX(),g.goal.nextX(),1)&&near(goal.nextZ(),g.goal.nextZ(),1)
            &&PlanningTask.rocket(rocket,g.rocket,4)&&gravity==g.gravity&&rate==g.rate;}
    }
    record Launch(FlightPrediction.State start,double yaw,double height,double gravity,int duration,boolean remaining,SurfaceLaunchPlanner.Plan plan,boolean wet) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){var water=wet?(SurfaceLaunchPlanner.Water)s:SurfaceLaunchPlanner.DRY;
            if(plan!=null)return SurfaceLaunchPlanner.pulseCost(start,plan,gravity,duration,s,water);
            return remaining?SurfaceLaunchPlanner.chooseRemaining(start,yaw,height,gravity,duration,s,water):SurfaceLaunchPlanner.choose(start,yaw,height,gravity,duration,s,water);}
        public boolean compatible(PlanningTask p){return p instanceof Launch g&&duration==g.duration&&remaining==g.remaining&&wet==g.wet&&near(yaw,g.yaw,6)&&near(height,g.height,1)&&java.util.Objects.equals(plan,g.plan);}
    }
    record Slope(FlightPrediction.State start,double x,double y,double z,double radius,double step) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){return SlopeSampler.sample(x,y,z,radius,step,s::support);}
        public boolean compatible(PlanningTask p){return p instanceof Slope g&&near(x,g.x,.5)&&near(y,g.y,.5)&&near(z,g.z,.5)&&radius==g.radius&&step==g.step;}
    }
    record Exit(FlightPrediction.State start,GlidePlanner.Request template,double height,double length,boolean piercing,double x,double z) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){return AttackExitPlanner.choose(start,template,height,length,piercing,x,z,s,(a,b,c)->s.segment(start.x(),start.y(),start.z(),a,b,c,template.sideMargin(),template.verticalMargin()));}
        public boolean compatible(PlanningTask p){return p instanceof Exit g&&piercing==g.piercing&&near(height,g.height,2)&&near(length,g.length,1)&&PlanningTask.request(template,g.template);}
    }
    record Chain(FlightPrediction.State start,FlightPrediction.Aim aim,double height,int rocket,double gravity,double minimum,int budget,double distance,GlidePlanner.Request primary) implements PlanningTask {
        public Object compute(CollisionSnapshot.Space s){return primary!=null?ChainAttackPlanner.primaryContact(start,primary,s):ChainAttackPlanner.choose(start,aim,height,rocket,gravity,minimum,budget,distance,s);}
        public boolean compatible(PlanningTask p){return p instanceof Chain g&&PlanningTask.aim(aim,g.aim)&&near(height,g.height,1)&&PlanningTask.rocket(rocket,g.rocket,3)&&budget==g.budget&&(primary==null?g.primary==null:g.primary!=null&&PlanningTask.request(primary,g.primary));}
    }
    record Sweep(FlightPrediction.State start,SweepingAttackPlanner.Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,double exit,double turn,
        SweepingAttackPlanner.Path path,boolean exiting,int budget,boolean ignition) implements PlanningTask {
        public Sweep(FlightPrediction.State start,SweepingAttackPlanner.Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,double exit,double turn,
            SweepingAttackPlanner.Path path,boolean exiting,int budget){this(start,target,aim,rocket,gravity,rate,minimum,exit,turn,path,exiting,budget,false);}
        public Object compute(CollisionSnapshot.Space s){SweepingAttackPlanner.Contact contact=(a,b,t)->s.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t);
            var initial=ignition?start.withRocketDelay(1):start;
            return path==null?SweepingAttackPlanner.choose(initial,target,aim,rocket,gravity,rate,minimum,exit,s,contact,turn)
                :SweepingAttackPlanner.simulate(initial,path,aim,rocket,gravity,rate,minimum,exiting,s,contact,budget);}
        public boolean compatible(PlanningTask p){return p instanceof Sweep g&&ignition==g.ignition&&path==g.path&&exiting==g.exiting&&PlanningTask.aim(aim,g.aim)&&PlanningTask.rocket(rocket,g.rocket,3)&&rate==g.rate&&budget==g.budget&&gravity==g.gravity&&minimum==g.minimum&&exit==g.exit;}
    }
    record SweepPulse(FlightPrediction.State start,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int duration,double gravity,double rate,double minimum,boolean exiting) implements PlanningTask {
        public Slot slot(){return new Slot(getClass(),duration,exiting?1:0);}
        public boolean compatible(PlanningTask p){return p instanceof SweepPulse s&&path==s.path&&duration==s.duration&&exiting==s.exiting&&PlanningTask.aim(aim,s.aim)&&gravity==s.gravity&&rate==s.rate&&minimum==s.minimum;}
        public Object compute(CollisionSnapshot.Space s){
            SweepingAttackPlanner.Contact contact=(a,b,t)->s.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t);
            double cost=0;
            for(int ticks=RocketProfile.minimumTicks(duration);ticks<=RocketProfile.maximumTicks(duration);ticks++){
                var prediction=SweepingAttackPlanner.simulate(start.withRocketDelay(1),path,aim,ticks,gravity,rate,minimum,exiting,s,contact,exiting?24:100);
                if(!prediction.safe()||!prediction.hit())return Double.POSITIVE_INFINITY;cost+=prediction.cost();
            }
            return cost/12+RocketProfile.expectedTicks(duration)*.02;
        }
    }
    record RejoinResult(SweepingAttackPlanner.Selection selection,int duration) {}
    record FunnelResult(AttackFunnelPlanner.Atlas atlas,java.util.Map<Long,CollisionSnapshot.Cell> coverage) {
        public FunnelResult {coverage=java.util.Map.copyOf(coverage);}
    }
    record Funnel(FlightPrediction.State start,AttackFunnelPlanner.Input input) implements PlanningTask {
        public boolean compatible(PlanningTask p){return p instanceof Funnel f&&PlanningTask.aim(input.aim(),f.input.aim())
            &&input.target().subtract(f.input.target()).length()<=.75&&input.rate()==f.input.rate()
            &&input.minimum()==f.input.minimum()&&input.exitLength()==f.input.exitLength();}
        public Object compute(CollisionSnapshot.Space s){
            var atlas=AttackFunnelPlanner.discover(input,s,(a,b,t)->s.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t));
            var accepted=new java.util.ArrayList<AttackFunnelPlanner.Window>();
            var coverage=new java.util.HashMap<Long,CollisionSnapshot.Cell>();
            for(var w:atlas.windows()){
                var proof=s.proofSpace();
                var roll=SweepingAttackPlanner.simulate(w.witness(),w.path(),input.aim(),w.rocket(),input.physics().gravity(),input.rate(),input.minimum(),false,proof,
                    (a,b,t)->proof.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t),120);
                if(!roll.safe()||!roll.hit())continue;
                var union=new java.util.HashMap<>(coverage);union.putAll(proof.coverage());
                if(union.size()>640)continue;
                accepted.add(w);coverage=union;
            }
            return new FunnelResult(new AttackFunnelPlanner.Atlas(accepted),coverage);
        }
    }
    record Capture(FlightPrediction.State start,AttackFunnelPlanner.Atlas atlas,FlightPrediction.Aim aim,int rocket,
                   double rate,double minimum,List<Integer> durations,boolean immediateOnly) implements PlanningTask {
        public Capture {durations=List.copyOf(durations);}
        public boolean compatible(PlanningTask p){return p instanceof Capture c&&atlas==c.atlas&&PlanningTask.aim(aim,c.aim)
            &&PlanningTask.rocket(rocket,c.rocket,2)&&rate==c.rate&&minimum==c.minimum&&durations.equals(c.durations)&&immediateOnly==c.immediateOnly;}
        public Object compute(CollisionSnapshot.Space s){return AttackFunnelPlanner.capture(start,atlas,aim,rocket,rate,minimum,durations,immediateOnly,s,
            (a,b,t)->s.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t));}
    }
    record Rejoin(FlightPrediction.State start,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,List<Integer> durations) implements PlanningTask {
        public Rejoin {durations=List.copyOf(durations);}
        public Slot slot(){return new Slot(getClass(),System.identityHashCode(path),0);}
        public boolean compatible(PlanningTask p){return p instanceof Rejoin r&&path==r.path&&PlanningTask.aim(aim,r.aim)&&PlanningTask.rocket(rocket,r.rocket,3)&&gravity==r.gravity&&rate==r.rate&&minimum==r.minimum&&durations.equals(r.durations);}
        public Object compute(CollisionSnapshot.Space s){
            SweepingAttackPlanner.Contact contact=(a,b,t)->s.ray(a.x(),a.y(),a.z(),b.x()+b.vx()*t,b.bottom()+b.height()*.5+b.vy()*t,b.z()+b.vz()*t);
            var coast=SweepingAttackPlanner.rejoin(start,path,aim,rocket,gravity,rate,minimum,s,contact);
            if(coast!=null)return new RejoinResult(coast,-1);
            if(rocket>0)return null;
            for(int duration:durations){
                var funded=SweepingAttackPlanner.rejoin(start.withRocketDelay(1),path,aim,RocketProfile.maximumTicks(duration),gravity,rate,minimum,s,contact);
                if(funded==null)continue;boolean safe=true;
                for(int ticks=RocketProfile.minimumTicks(duration);ticks<=RocketProfile.maximumTicks(duration);ticks++){
                    var prediction=SweepingAttackPlanner.simulate(start.withRocketDelay(1),funded.path(),aim,ticks,gravity,rate,minimum,false,s,contact,120);
                    if(!prediction.safe()||!prediction.hit()){safe=false;break;}
                }
                if(safe)return new RejoinResult(funded,duration);
            }
            return null;
        }
    }
}
