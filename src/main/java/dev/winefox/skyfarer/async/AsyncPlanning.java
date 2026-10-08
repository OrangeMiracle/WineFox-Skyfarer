package dev.winefox.skyfarer.async;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.*;
import java.util.List;

/** Main-thread facade. Live callbacks in legacy call sites are deliberately NOT stored in DTOs. */
public final class AsyncPlanning {
    /** Main-thread adapter only; only its numeric bounds are copied into a worker task. */
    public record BoundedCorridor(CollisionSnapshot.Bounds bounds,GlidePlanner.Corridor live) implements GlidePlanner.Corridor {
        public boolean clear(FlightPrediction.State a,FlightPrediction.State b,double side,double vertical){return Math.hypot(b.x()-bounds.x(),b.z()-bounds.z())<=bounds.radius()&&live.clear(a,b,side,vertical);}
    }
    private static PlanningTask bounded(PlanningTask task,GlidePlanner.Corridor space){return space instanceof BoundedCorridor b?new PlanningTask.Bounded(task,b.bounds):task;}
    private final PlannerSession session;
    private boolean waiting,lastPending;
    private PlanningTask.GlideResult lastGlide;
    public double clearanceCost(){return lastGlide==null?Double.POSITIVE_INFINITY:lastGlide.narrowCost();}
    private record ApprovedPulse(GlidePlanner.Request request,PlanningTask.PulseResult result) {}
    private final java.util.ArrayList<ApprovedPulse> approvedPulses=new java.util.ArrayList<>();
    public AsyncPlanning(EntityMaid maid){session=new PlannerSession(new MaidTerrainSource(maid));}
    public PhysicsContext physics(){return session.physics();}
    public void begin(long tick,Object phase,Object target,long revision){waiting=false;approvedPulses.clear();session.begin(tick,phase,target,revision,PredictionHorizon.factor());}
    public boolean waiting(){return waiting;}
    public boolean lastPending(){return lastPending;}
    public void close(){session.close();}
    public void invalidate(){session.invalidate();}
    public EmergencyAvoidancePlanner.Plan emergency(FlightPrediction.State s,int active,List<Integer> durations,double gravity){
        var candidate=(EmergencyAvoidancePlanner.Plan)optional(new PlanningTask.Emergency(s,active,durations,gravity),null);
        return session.recertifyEmergency(s,candidate,active,gravity);
    }
    public LaunchAttackPlanner.Plan launchAttack(FlightPrediction.State s,List<LaunchAttackPlanner.Target> targets,List<Integer> durations,double gravity,double rate,double minimum,double exit){
        return (LaunchAttackPlanner.Plan)call(new PlanningTask.LaunchAttack(s,targets,durations,gravity,rate,minimum,exit));
    }
    public LaunchAttackPlanner.Plan optionalLaunchAttack(FlightPrediction.State s,List<LaunchAttackPlanner.Target> targets,double gravity,double rate,double minimum,double exit){
        return (LaunchAttackPlanner.Plan)optional(new PlanningTask.LaunchAttack(s,targets,List.of(),gravity,rate,minimum,exit),null);
    }
    public int pending(){return session.pendingCount();}
    public RiptidePlanner.Plan propulsion(FlightPrediction.State start,PropulsionDemand demand,float strength,boolean wet,GlidePlanner.Corridor bounds){
        var proposal=(RiptidePlanner.Plan)optional(bounded(new PlanningTask.Propulsion(start,demand,strength,physics().gravity(),wet),bounds),null);
        return proposal==null?null:proposal.withDemand(demand.path()==null&&proposal.demand()!=null&&proposal.demand().path()!=null?proposal.demand().withAim(demand.aim()):demand);
    }
    public RiptidePlanner.Plan certifyRiptide(FlightPrediction.State s,RiptidePlanner.Plan p,int elapsed,boolean wet) {
        return session.recertifyRiptide(s,p,elapsed,wet);
    }
    public String riptideRejection(){return session.riptideRejection();}
    public int cells(){return session.cellCount();}
    public String detail(){return session.detail();}
    private SweepingAttackPlanner.Search sweepSearch;
    private boolean sweepSearchPending;
    private String sweepSource="COAST";
    public String attackDetail(){return "source="+sweepSource+" search="+(sweepSearchPending?"PENDING":sweepSearch==null?"NONE":"candidates="+sweepSearch.candidates()+" geometry="+sweepSearch.geometryRejected()+" noContact="+sweepSearch.contactRejected()+" exit="+sweepSearch.exitRejected()+" accepted="+sweepSearch.accepted()+" miss="+sweepSearch.bestMiss())+" rejoin="+session.rejoinReason();}
    public List<GlidePlanner.TracePoint> trace(){return session.trace();}
    private Object call(PlanningTask task){lastPending=false;try{return session.query(task);}catch(PlannerSession.Pending pending){lastPending=true;waiting=true;throw pending;}}
    public AttackLanePlanner.Lane lane(AttackLanePlanner.Input input){return (AttackLanePlanner.Lane)call(new PlanningTask.Lane(input));}
    private Object optional(PlanningTask task,Object fallback){try{return call(task);}catch(PlannerSession.Pending pending){waiting=true;return fallback;}}
    public GlidePlanner.Plan glide(FlightPrediction.State s,GlidePlanner.Request r,GlidePlanner.Corridor ignored){
        var result=(PlanningTask.GlideResult)optional(bounded(new PlanningTask.Glide(s,r),ignored),null);
        lastGlide=result;
        if(result!=null)return result.plan();
        if(r.rocketTicks()>0)for(var approved:approvedPulses)if(PlanningTask.request(r.withPulse(0),approved.request.withPulse(0))){
            var plan=approved.result.plans().get(r.rocketTicks());if(plan!=null)return plan;
        }
        double goal=FlightPrediction.pathHeight(r.distance(),r.height(),r.slope(),r.flare());
        double pitch=GlidePlanner.feedbackPitch(s,goal,FlightPrediction.pathVerticalSpeed(s,r.distance(),r.slope(),r.flare()),r.gravity(),r.rocketTicks()>0);
        return new GlidePlanner.Plan(r.yaw(),pitch,r.yawRate(),r.pitchRate(),GlidePlanner.Mode.GLIDE,false,0,0,1,0);
    }
    public GlidePlanner.Forecast forecast(FlightPrediction.State s,GlidePlanner.Request r,GlidePlanner.Plan p,GlidePlanner.Corridor ignored,double speed){
        var result=(PlanningTask.GlideResult)optional(bounded(new PlanningTask.Glide(s,r),ignored),null);if(result==null)return new GlidePlanner.Forecast(false,Double.NaN);
        var f=result.forecast();return new GlidePlanner.Forecast(f.safe(),f.reserve()-.5*speed*speed,f.passed(),f.miss(),f.reason());
    }
    public GlidePlanner.Forecast certifyAttack(FlightPrediction.State s,GlidePlanner.Request r,double minimum,GlidePlanner.Corridor space){
        // A pending certificate must veto commitment without aborting this tick's
        // ordinary steering/energy update (especially after a descending handoff).
        return (GlidePlanner.Forecast)optional(bounded(new PlanningTask.AttackCertificate(s,r,minimum),space),
            new GlidePlanner.Forecast(false,Double.NaN,false,Double.POSITIVE_INFINITY,"async_pending"));
    }
    public GlidePlanner.PulseReview reviewPulse(FlightPrediction.State s,GlidePlanner.Request r,GlidePlanner.Corridor ignored,int duration,double speed,double reserve){
        var result=(PlanningTask.PulseResult)optional(bounded(new PlanningTask.Pulse(s,r,duration,speed,reserve),ignored),null);
        if(result==null)return new GlidePlanner.PulseReview(Double.POSITIVE_INFINITY,"async_pending");
        if(Double.isFinite(result.review().cost()))approvedPulses.add(new ApprovedPulse(r,result));return result.review();
    }
    public double sweepPulse(FlightPrediction.State s,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int duration,double gravity,double rate,double minimum,boolean exiting,GlidePlanner.Corridor space){
        return (Double)optional(bounded(new PlanningTask.SweepPulse(s,path,aim,duration,gravity,rate,minimum,exiting),space),Double.POSITIVE_INFINITY);
    }
    public double pulseCost(FlightPrediction.State s,GlidePlanner.Request r,GlidePlanner.Corridor ignored,int duration,double speed,double reserve){return reviewPulse(s,r,ignored,duration,speed,reserve).cost();}
    public CompactTurnPlanner.Plan compact(FlightPrediction.State s,CompactTurnPlanner.Goal goal,int rocket,double gravity,double rate,GlidePlanner.Corridor ignored){
        var p=(CompactTurnPlanner.Plan)optional(bounded(new PlanningTask.Compact(s,goal,rocket,gravity,rate,-1),ignored),new CompactTurnPlanner.Plan(null,0,0,Math.hypot(s.vx(),s.vz()),-25));
        return p==null?null:new CompactTurnPlanner.Plan(CompactTurnPlanner.command(s,goal,gravity,rocket>0,rate,p.brake()),p.cost(),p.excursion(),p.speed(),p.brake());
    }
    public double compactPulse(FlightPrediction.State s,CompactTurnPlanner.Goal goal,int duration,double gravity,double rate,GlidePlanner.Corridor ignored){return (Double)optional(bounded(new PlanningTask.Compact(s,goal,0,gravity,rate,duration),ignored),Double.POSITIVE_INFINITY);}
    public SurfaceLaunchPlanner.Plan launch(FlightPrediction.State s,double yaw,double height,double gravity,int duration,GlidePlanner.Corridor ignored,SurfaceLaunchPlanner.Water water){return (SurfaceLaunchPlanner.Plan)call(new PlanningTask.Launch(s,yaw,height,gravity,duration,false,null,water!=SurfaceLaunchPlanner.DRY));}
    public SurfaceLaunchPlanner.Plan coastLaunch(FlightPrediction.State s,double yaw,double height,double gravity,SurfaceLaunchPlanner.Water water){return (SurfaceLaunchPlanner.Plan)call(new PlanningTask.Launch(s,yaw,height,gravity,-1,false,null,water!=SurfaceLaunchPlanner.DRY));}
    public SurfaceLaunchPlanner.Plan launchRemaining(FlightPrediction.State s,double yaw,double height,double gravity,int ticks,GlidePlanner.Corridor ignored,SurfaceLaunchPlanner.Water water){return (SurfaceLaunchPlanner.Plan)call(new PlanningTask.Launch(s,yaw,height,gravity,ticks,true,null,water!=SurfaceLaunchPlanner.DRY));}
    public double launchPulse(FlightPrediction.State s,SurfaceLaunchPlanner.Plan p,double gravity,int duration,GlidePlanner.Corridor ignored,SurfaceLaunchPlanner.Water water){return (Double)call(new PlanningTask.Launch(s,p.yaw(),p.height(),gravity,duration,false,p,water!=SurfaceLaunchPlanner.DRY));}
    public SlopeSampler.Estimate slope(double x,double y,double z,double radius,double step,SlopeSampler.Support ignored){return (SlopeSampler.Estimate)optional(new PlanningTask.Slope(CollisionSnapshot.point(x,y,z),x,y,z,radius,step),new SlopeSampler.Estimate(0,0,0,0,0));}
    public AttackExitPlanner.Choice exit(FlightPrediction.State s,GlidePlanner.Request r,double height,double length,boolean pierce,double x,double z,GlidePlanner.Corridor ignored,AttackExitPlanner.Lane lane){return (AttackExitPlanner.Choice)call(new PlanningTask.Exit(s,r,height,length,pierce,x,z));}
    public ChainAttackPlanner.Plan chain(FlightPrediction.State s,FlightPrediction.Aim aim,double height,int rocket,double gravity,double minimum,int budget,double distance,GlidePlanner.Corridor ignored){return (ChainAttackPlanner.Plan)optional(bounded(new PlanningTask.Chain(s,aim,height,rocket,gravity,minimum,budget,distance,null),ignored),null);}
    public FlightPrediction.State primaryContact(FlightPrediction.State s,GlidePlanner.Request r,GlidePlanner.Corridor ignored){return (FlightPrediction.State)optional(new PlanningTask.Chain(s,r.aim(),r.height(),r.rocketTicks(),r.gravity(),0,0,0,r),null);}
    public SweepingAttackPlanner.Selection sweep(FlightPrediction.State s,SweepingAttackPlanner.Point target,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,double exit,GlidePlanner.Corridor ignored,SweepingAttackPlanner.Contact contact,double turn){
        sweepSearch=(SweepingAttackPlanner.Search)optional(bounded(new PlanningTask.Sweep(s,target,aim,rocket,gravity,rate,minimum,exit,turn,null,false,120),ignored),null);
        sweepSearchPending=lastPending;sweepSource="COAST_OR_ACTIVE_ROCKET";
        return sweepSearch==null?null:sweepSearch.selection();
    }
    /** Speculative inventory-funded geometry only; Rejoin still certifies every lifetime before ignition. */
    public SweepingAttackPlanner.Selection fundedSweep(FlightPrediction.State s,SweepingAttackPlanner.Point target,FlightPrediction.Aim aim,int duration,double gravity,double rate,double minimum,double exit,GlidePlanner.Corridor space,double turn){
        sweepSearch=(SweepingAttackPlanner.Search)optional(bounded(new PlanningTask.Sweep(s,target,aim,RocketProfile.maximumTicks(duration),gravity,rate,minimum,exit,turn,null,false,120,true),space),null);
        sweepSearchPending=lastPending;sweepSource="FUNDED:"+duration;
        return sweepSearch==null?null:sweepSearch.selection();
    }
    public PlanningTask.RejoinResult rejoin(FlightPrediction.State s,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,GlidePlanner.Corridor space,List<Integer> durations){
        var task=new PlanningTask.Rejoin(s,path,aim,rocket,gravity,rate,minimum,durations);
        var proposal=(PlanningTask.RejoinResult)optional(bounded(task,space),null);
        return session.recertifyRejoin(task,proposal,space instanceof BoundedCorridor b?b.bounds():null);
    }
    public PlanningTask.RejoinResult currentSweep(FlightPrediction.State s,SweepingAttackPlanner.Selection choice,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,GlidePlanner.Corridor space){
        var task=new PlanningTask.Rejoin(s,choice.path(),aim,rocket,gravity,rate,minimum,List.of());
        lastPending=false;
        return session.recertifyRejoin(task,new PlanningTask.RejoinResult(choice,-1),space instanceof BoundedCorridor b?b.bounds():null);
    }
    public PlanningTask.RejoinResult launchRejoin(FlightPrediction.State s,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,GlidePlanner.Corridor space,List<Integer> durations){
        var task=new PlanningTask.Rejoin(s,path,aim,rocket,gravity,rate,minimum,durations,true);
        var proposal=(PlanningTask.RejoinResult)optional(bounded(task,space),null);
        return session.recertifyRejoin(task,proposal,space instanceof BoundedCorridor b?b.bounds():null);
    }
    public SweepingAttackPlanner.Rollout follow(FlightPrediction.State s,SweepingAttackPlanner.Path path,FlightPrediction.Aim aim,int rocket,double gravity,double rate,double minimum,boolean exiting,GlidePlanner.Corridor ignored,SweepingAttackPlanner.Contact contact,int budget){
        var result=(SweepingAttackPlanner.Rollout)optional(bounded(new PlanningTask.Sweep(s,null,aim,rocket,gravity,rate,minimum,0,0,path,exiting,budget),ignored),
            new SweepingAttackPlanner.Rollout(true,false,-1,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,SweepingAttackPlanner.command(s,path,aim,exiting,rocket,gravity,rate),List.of()));
        return new SweepingAttackPlanner.Rollout(result.safe(),result.hit(),result.contactTick(),result.cost(),result.miss(),
            SweepingAttackPlanner.command(s,path,aim,exiting,rocket,gravity,rate),result.trace(),result.envelope());
    }
}
