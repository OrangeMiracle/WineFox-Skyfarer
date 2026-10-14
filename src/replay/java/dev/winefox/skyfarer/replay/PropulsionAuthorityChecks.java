package dev.winefox.skyfarer.replay;

import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PropulsionAuthorityChecks {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("pressure")){System.setProperty("wfs.cometTickMillis","5");CometChecks.replay(true,true);PlannerSession.stop();return;}
        if(args.length>0&&args[0].equals("replay")){CometChecks.replay(false,true);CometChecks.replay(true,true);PlannerSession.stop();return;}
        System.setProperty("wfs.replayTickMillis","2");
        exclusive();switching();feedback();sustainedProvider();squadHold();effects();
        CometChecks.baublesAndSaving();UnifiedPropulsionChecks.emergencyExecution();CometChecks.squad();
        PlannerSession.stop();System.out.println("Source authority, continuous control, source switching, effects and compatibility passed");
    }
    static void exclusive(){
        var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);
        maid.baubles.setStackInSlot(0,CometChecks.ordinary());maid.baubles.setStackInSlot(1,CometChecks.eternal());
        var offers=new ArrayList<PropulsionDemand.Source>();
        var providers=new ArrayList<PropulsionResolver.Provider>();
        for(var rule:PropulsionResolver.RULES)providers.add(new PropulsionResolver.Provider(){
            public PropulsionDemand.Source source(){return rule.source();}
            public PropulsionResolver.Offer offer(PropulsionResolver.Context c){offers.add(source());return new PropulsionResolver.Offer(false,true,"DELAYED_OR_UNSAFE");}
        });
        for(var phase:PropulsionDemand.Phase.values()){
            var resolver=new PropulsionResolver(providers);
            for(int tick=0;tick<65;tick++){
                offers.clear();var demand=new PropulsionDemand(phase,0,85,100,.9);
                var result=resolver.resolve(new PropulsionResolver.Context(maid,null,demand,null,null,null,null,null,null),tick%2==0,true);
                check(result.source()==null&&offers.equals(List.of(PropulsionDemand.Source.VIRTUAL_PROPULSION)),"exclusive timeout fallback in "+phase);
            }
        }
        for(boolean transit:List.of(false,true)){
            var motor=new FireworkManager(transit);int[] invoked={0};
            check(!motor.boost(maid,d->{invoked[0]++;return 0;})&&invoked[0]==0,"legacy direct launch bypassed authority");
        }
        var snapshot=PropulsionCapability.inspect(maid);
        check(snapshot.virtual()&&snapshot.fireworks().isEmpty()&&snapshot.riptide().isEmpty()&&!CometBaubles.saving(maid),"exclusive equipment advertised consumables or stacked savings");
        check(maid.inventory.consumed==0&&world.rockets.isEmpty()&&maid.random.draws==0,"speculation consumed/RNG/spawned");
        System.out.println("Every phase: exclusive virtual source survives pending/timeout/emergency; legacy firework ports denied, no resources or RNG");
    }
    static void switching(){
        var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);maid.rain=false;
        var source=new PropulsionCapability();var motor=new RiptidePropulsion();var rocket=new FireworkManager();var planning=new AsyncPlanning(maid);var heading=new HeadingController();
        var demand=new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,0,85,100,.9);
        world.advance(1);planning.begin(1,"SWITCH",null,0);heading.beginTick(maid);
        var first=source.decide(maid,EquipmentLifecycleChecks.state(maid),demand,false,false,motor,rocket,planning,heading,UnifiedPropulsionChecks.OPEN,d->0);
        check(first.firework()&&rocket.active(),"ordinary fallback unavailable");int consumed=maid.inventory.consumed;
        maid.baubles.setStackInSlot(0,CometChecks.eternal());world.advance(2);planning.begin(2,"SWITCH",null,0);heading.beginTick(maid);
        source.decide(maid,EquipmentLifecycleChecks.state(maid),demand,false,false,motor,rocket,planning,heading,UnifiedPropulsionChecks.OPEN,d->0);
        check(!rocket.active()&&!motor.active()&&maid.inventory.consumed==consumed,"equipping heart retained old source/consumption");
        maid.baubles.setStackInSlot(0,ItemStack.EMPTY);source.refresh(maid,motor,rocket);source.synchronize(planning);
        check(PropulsionResolver.permits(maid,PropulsionDemand.Source.FIREWORK),"unequip permanently blocked fallback");
        planning.close();rocket.detach();
        var changed=new FireworkManager();world.time+=100;world.onRocketSpawn=()->maid.baubles.setStackInSlot(0,CometChecks.eternal());
        check(!changed.boost(maid,d->0)&&maid.inventory.consumed==consumed,"spawn callback equipment switch bypassed commit gate");
        check(world.rockets.stream().noneMatch(r->r.isAlive()),"vetoed source left propulsion entity");
        System.out.println("Live source change cancels old execution; spawn callback cannot consume after exclusive equipment appears");
    }
    static void feedback(){
        var state=new FlightPrediction.State(0,80,0,0,0,1.2,0,0,PhysicsContext.VANILLA);
        double maxError=0,minSpeed=10,maxSpeed=0,maxForce=0;int powered=0;
        for(int tick=0;tick<700;tick++){
            double yaw=tick<180?0:tick<360?(tick-180)*.5:90;
            double height=tick<360?80:Math.min(90,80+(tick-360)*.04);
            double angle=Math.toRadians(yaw);
            var demand=new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,state.x()-Math.sin(angle)*100,height,state.z()+Math.cos(angle)*100,1);
            var candidate=VirtualFlightControl.candidate(state,demand);
            var proof=RiptidePlanner.certify(state,candidate,0,.08,(a,b,h,v)->b.y()>1,SurfaceLaunchPlanner.DRY);
            check(proof!=null,"cruise/turn/climb lost finite proof at "+tick+" state="+state);
            var next=RiptidePlanner.step(state,proof,0,.08,false);var command=RiptidePlanner.command(state,proof,0,.08);
            var aimed=RiptidePlanner.aimLaunch(state,FlightPrediction.turn(state.yaw(),command.yaw(),command.yawRate()),FlightPrediction.turn(state.pitch(),command.pitch(),command.pitchRate()));
            float force=proof.profile().force(state.vx(),state.vy(),state.vz());
            var actual=VanillaElytra.step(RiptidePlanner.push(aimed,force),aimed.yaw(),aimed.pitch(),.08,false);
            check(next.equals(actual),"closed-loop execution differs from prediction");
            check(proof.trace().size()<=25,"unbounded cruise horizon");
            state=next;powered+=force>.001?1:0;maxForce=Math.max(maxForce,force);
            if(tick>80){double speed=Math.hypot(state.vx(),state.vz());minSpeed=Math.min(minSpeed,speed);maxSpeed=Math.max(maxSpeed,speed);maxError=Math.max(maxError,Math.abs(state.y()-height));}
        }
        System.out.println("700-tick cruise/turn/climb: speed="+minSpeed+".."+maxSpeed+" heightError="+maxError+" powered="+powered+" maxForce="+maxForce);
        check(powered>650&&minSpeed>1&&maxSpeed<1.5&&maxError<1,"speed/height hold oscillated or stalled");
    }
    static void sustainedProvider()throws Exception{
        var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);world.loadedRadius=1024;
        maid.velocity=new Vec3(0,0,1.2);maid.baubles.setStackInSlot(0,CometChecks.eternal());maid.baubles.setStackInSlot(1,CometChecks.ordinary());
        var provider=new FlightPropulsion();var planning=new AsyncPlanning(maid);var heading=new HeadingController();int powered=0;
        double height=maid.getEyeY(),minimum=10,maxError=0;
        for(int tick=1;tick<=220;tick++){
            world.advance(tick);planning.begin(tick,"HOLD",null,0);heading.beginTick(maid);provider.prepare(maid,true);
            var start=EquipmentLifecycleChecks.state(maid);var demand=new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,0,height,700,1);
            if(provider.sustain(maid,start,demand,planning,heading))powered++;
            int pushes=maid.pushCalls;provider.sustain(maid,start,demand,planning,heading);check(pushes==maid.pushCalls,"cruise stacked force same tick");
            var next=FlightPrediction.step(EquipmentLifecycleChecks.state(maid),maid.yaw,maid.pitch,180,90,90,.08,false);
            check(new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(start,next,0,0),"sustained controller collision");
            EquipmentLifecycleChecks.move(maid,next);minimum=Math.min(minimum,maid.velocity.horizontalDistance());maxError=Math.max(maxError,Math.abs(maid.getEyeY()-height));
            check(planning.cells()<=768&&planning.pending()<=2,"unbounded sustained planning");
        }
        System.out.println("Actual provider speed hold: powered="+powered+"/220 minSpeed="+minimum+" heightError="+maxError);
        check(powered>200&&minimum>1&&maxError<1&&maid.inventory.consumed==0&&maid.offhand.damage==0&&maid.random.draws==0&&world.rockets.isEmpty(),"sustained output/zero consumption violated");
        world.advance(221);planning.begin(221,"HOLD",null,0);heading.beginTick(maid);
        var before=maid.velocity;int pushes=maid.pushCalls;
        world.boxes.add(new FlightCorridor.Box(-2,78,maid.getZ()+.6,2,84,maid.getZ()+2));
        try(var blocked=TrajectoryContinuityChecks.blockWorkers()){
            check(!provider.sustain(maid,EquipmentLifecycleChecks.state(maid),new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,0,height,700,1),planning,heading),"new live wall allowed cruise during worker blackout");
        }
        check(maid.velocity==before&&maid.pushCalls==pushes,"wall rejection added thrust or removed inertia");
        provider.close(maid,"TEST",false);planning.close();
    }
    static void squadHold(){
        var world=new ReplayWorld();world.loadedRadius=512;
        var maids=new ArrayList<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid>();
        var providers=new ArrayList<FlightPropulsion>();var planners=new ArrayList<AsyncPlanning>();var headings=new ArrayList<HeadingController>();
        var counts=new int[8];
        for(int i=0;i<8;i++){
            var maid=RiptideReleaseChecks.maid(world);maid.pos=new Vec3(i*2,80,0);maid.velocity=new Vec3(0,0,1.2);maid.baubles.setStackInSlot(0,CometChecks.eternal());
            maids.add(maid);providers.add(new FlightPropulsion());planners.add(new AsyncPlanning(maid));headings.add(new HeadingController());
        }
        for(int tick=1;tick<=100;tick++){
            world.advance(tick);
            for(int i=0;i<8;i++){
                var maid=maids.get(i);var planning=planners.get(i);var provider=providers.get(i);var heading=headings.get(i);
                planning.begin(tick,"SQUAD_HOLD",null,0);heading.beginTick(maid);provider.prepare(maid,true);
                if(provider.sustain(maid,EquipmentLifecycleChecks.state(maid),new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,maid.getX(),80.4,350,1),planning,heading))counts[i]++;
                EquipmentLifecycleChecks.move(maid,FlightPrediction.step(EquipmentLifecycleChecks.state(maid),maid.yaw,maid.pitch,180,90,90,.08,false));
                check(planning.cells()<=768&&planning.pending()<=2&&maid.inventory.consumed==0&&maid.offhand.damage==0,"squad resource/thread/capture budget violation");
            }
        }
        for(int i=0;i<8;i++){check(counts[i]>85,"squad sustained provider starved: "+Arrays.toString(counts));providers.get(i).close(maids.get(i),"TEST",false);planners.get(i).close();}
        check(world.rockets.isEmpty(),"squad spawned propulsion entities");
        System.out.println("Eight simultaneous speed-hold providers: certified ticks per 100="+Arrays.toString(counts)+", bounded cells/jobs and zero entities");
    }
    static void effects(){
        var gate=new ThrustEffectGate();int packets=0,rings=0;long last=-1000;
        for(int tick=0;tick<200;tick++){
            float power=tick<60?.06f:tick<100?.16f:tick<110?0:.12f;
            var frame=gate.actual(tick,power);check(gate.actual(tick,power)==null&&gate.finish(tick)==null,"multiple visual events per tick");
            if(frame!=null){packets++;if(frame.ring()){check(tick-last>=20,"ring cooldown broken");rings++;last=tick;}}
        }
        var stopped=gate.finish(200);check(stopped!=null&&stopped.power()==0&&!stopped.ring(),"silent provider left visuals active");
        check(rings==3&&packets<=55,"effects spammed or missed real output increase "+rings+" packets="+packets);
        for(var direction:List.of(new Vec3(0,0,1),new Vec3(1,1,0),new Vec3(0,2,0),new Vec3(.01,2,.01),Vec3.ZERO)){
            var frame=ThrustVisuals.frame(direction.x,direction.y,direction.z,37,-22);
            check(Math.abs(frame.forward().dot(frame.right()))<1e-9&&Math.abs(frame.forward().dot(frame.up()))<1e-9,"particle frame not perpendicular");
            check(frame.right().scale(.3).add(frame.right().scale(-.3)).dot(frame.right())==0,"tails not symmetric");
            for(int i=0;i<ThrustVisuals.RING_POINTS;i++){
                var radial=frame.radial(i*Math.PI/8);
                check(Math.abs(radial.dot(frame.forward()))<1e-9&&Math.abs(radial.dot(radial)-1)<1e-9&&radial.scale(.3).dot(radial.scale(.09))>0,"ring not planar/outward");
            }
        }
        var budget=new ThrustVisuals.Budget();int trail=0,ring=0;
        for(int i=0;i<1000;i++){if(budget.take(1,1,false))trail++;if(budget.take(1,16,true))ring+=16;}
        check(trail==96&&ring==64&&budget.take(2,16,true),"client particle bounds or reset failed");
        System.out.println("Actual impulse visual gate: onset/increase/restart rings=3, packets="+packets+"/200 ticks, stop and same-tick dedup passed");
    }
}
