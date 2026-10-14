package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.async.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.config.*;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.item.CometProgress;
import dev.winefox.skyfarer.runtime.CometEvolution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Executes production bauble/evolution/provider/controller code; external game APIs are fixtures. */
public final class CometChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ItemStack ordinary(){return new ItemStack(WineFoxSkyfarer.COMET_HEART.get());}
    static ItemStack eternal(){return new ItemStack(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get());}
    public static void main(String[] args)throws Exception {
        System.setProperty("wfs.replayTickMillis","10");
        if(args.length==0||args[0].equals("contracts")){baublesAndSaving();evolution();physics();provider();preemption();squad();}
        if(args.length==0||args[0].equals("replay")){replay(false);replay(true);}
        PlannerSession.stop();
    }
    static void baublesAndSaving(){
        SkyfarerSettings.reset("all");var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);
        var manager=new com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager();CometBaubles.bind(manager);
        require(manager.bound.size()==2,"native bauble binding missing");
        maid.offhand=eternal();require(!CometBaubles.virtual(maid),"hand granted virtual propulsion");
        maid.baubles.setStackInSlot(0,ordinary());require(CometBaubles.saving(maid),"ordinary bauble not recognized");
        maid.baubles.setStackInSlot(1,eternal());require(CometBaubles.virtual(maid)&&!CometBaubles.saving(maid),"effects stacked");
        maid.task=false;require(!CometBaubles.virtual(maid)&&!CometBaubles.saving(maid),"other task gained effect");maid.task=true;
        maid.baubles.setStackInSlot(1,ItemStack.EMPTY);
        var config=SkyfarerConfig.PARAMETERS.get("comet_firework_saving_chance");require(config.initial()==.6&&config.min()==.5&&config.max()==.7,"saving config range");
        var motor=new FireworkManager();int saved=0;
        for(int i=0;i<1000;i++){
            world.time+=40;maid.inventory.count=64;
            require(motor.boost(maid,d->0),"certified boost failed");
            if(maid.inventory.count==64)saved++;
            require(motor.active()&&motor.remainingBoostTicks()>0,"saved fuel lost actual thrust");motor.detach();
        }
        require(saved==600&&maid.random.draws==1000,"one independent 60% decision per actual success");
        world.time+=40;world.rejectRocket=true;require(!motor.boost(maid,d->0)&&maid.random.draws==1000,"rejected spawn sampled RNG");
        world.rejectRocket=false;require(!motor.boost(maid,d->Double.POSITIVE_INFINITY)&&maid.random.draws==1000,"rejected prediction sampled RNG");
        maid.baubles.setStackInSlot(0,ItemStack.EMPTY);maid.inventory.count=64;
        require(motor.boost(maid,d->0)&&maid.inventory.count==63&&maid.random.draws==1000,"ordinary firework behavior changed");motor.detach();
        System.out.println("Native slot lookup, non-stacking, Skyfarer scope, 1000 successful launches / 600 savings and rejected-launch RNG checks passed");
    }
    static Creeper dead(ServerLevel world){var creeper=new Creeper(world);creeper.alive=false;return creeper;}
    static void evolution(){
        var world=new ServerLevel();var maid=new EntityMaid(world,Vec3.ZERO);maid.owner=new Player(world,Vec3.ZERO);
        var heart=ordinary();maid.baubles.setStackInSlot(0,heart);
        var first=dead(world);CometEvolution.killed(first,new DamageSource(maid));CometEvolution.killed(first,new DamageSource(maid));
        require(CometProgress.read(heart)==1,"duplicate victim credited twice");
        CometEvolution.killed(new Creeper(world),new DamageSource(maid));CometEvolution.killed(dead(world),new DamageSource(maid.owner));
        require(CometProgress.read(heart)==1,"alive/other attacker credited");
        for(int i=1;i<99;i++)CometEvolution.killed(dead(world),new DamageSource(maid));
        var wearer=new EntityMaid(world,Vec3.ZERO);wearer.owner=maid.owner;maid.baubles.setStackInSlot(0,ItemStack.EMPTY);wearer.baubles.setStackInSlot(0,heart);
        require(CometProgress.read(wearer.baubles.getStackInSlot(0))==99,"wearer transfer lost stack progress");
        CometEvolution.killed(dead(world),new DamageSource(wearer));require(CometBaubles.virtual(wearer)&&wearer.baubles.getStackInSlot(0).getCount()==1,"confirmed kill did not replace one heart");
        require(dev.winefox.skyfarer.runtime.CometRewards.INSTANCE.owners.size()==1,"reward owner/deduplication failed");
        System.out.println("Kill evolution: confirmed source, duplicate death, transferred stack progress and owner entitlement passed");
    }
    static void physics(){
        var start=new FlightPrediction.State(0,30,0,0,-.1,.7,0,0,PhysicsContext.VANILLA);
        for(var phase:PropulsionDemand.Phase.values()){
            if(phase==PropulsionDemand.Phase.REJOIN||phase==PropulsionDemand.Phase.STRIKE)continue;
            var s=phase==PropulsionDemand.Phase.TAKEOFF?new FlightPrediction.State(0,.82,0,0,.3332,0,0,0,PhysicsContext.VANILLA):start;
            var d=new PropulsionDemand(phase,0,phase==PropulsionDemand.Phase.TAKEOFF?7:35,120,.9);
            var p=RiptidePlanner.chooseVirtual(s,d,.08,UnifiedPropulsionChecks.OPEN,SurfaceLaunchPlanner.DRY);
            require(p!=null&&p.profile().virtual()&&p.duration()<=30,"no bounded virtual plan: "+phase);
            var current=p.trace().getFirst();
            for(int tick=0;tick<p.trace().size()-1;tick++){
                current=RiptidePlanner.step(current,p,tick,.08,false);
                require(current.equals(p.trace().get(tick+1)),"predicted virtual force diverged: "+phase);
            }
            require(RiptidePlanner.certify(s,p,0,.08,(a,b,h,v)->false,SurfaceLaunchPlanner.DRY)==null,"virtual force bypassed obstacle");
            System.out.println("virtual "+phase+" acceleration="+p.profile().acceleration()+" duration="+p.duration());
        }
        var aim=new FlightPrediction.Aim(0,8,30,.3,5,.08,0,0,0,2.25,.1,10);
        var s=new FlightPrediction.State(0,12,0,0,-.1,.8,0,0,PhysicsContext.VANILLA);
        var d=new PropulsionDemand(PropulsionDemand.Phase.REJOIN,0,11,30,.2,null,aim,8,false,100);
        var p=RiptidePlanner.chooseVirtual(s,d,.08,UnifiedPropulsionChecks.OPEN,SurfaceLaunchPlanner.DRY);
        require(p!=null&&p.demand().path()!=null,"moving strike/exit virtual plan missing");
        require(RiptidePlanner.certify(s,p.withDemand(p.demand().withDeadline(0)),0,.08,UnifiedPropulsionChecks.OPEN,SurfaceLaunchPlanner.DRY)==null,"strike deadline relaxed");
        for(var profile:ThrustProfile.VIRTUAL){require(profile.force(0,0,3)==0,"accelerated beyond cap");require(profile.force(0,0,2.39)<=.010001,"speed taper overshot");}
        System.out.println("Moving strike/exit, deadline failure, swept collision rejection and continuous speed taper passed");
    }
    static void provider(){
        var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);maid.baubles.setStackInSlot(0,eternal());
        var provider=new FlightPropulsion();var planning=new AsyncPlanning(maid);var heading=new HeadingController();
        var demand=new PropulsionDemand(PropulsionDemand.Phase.AIR_RECOVERY,0,85,100,.9);boolean started=false;
        for(int tick=1;tick<=150;tick++){
            world.advance(tick);planning.begin(tick,"VIRTUAL_TEST",null,0);heading.beginTick(maid);provider.prepare(maid,true);
            if(!started){if(provider.prefer(maid,EquipmentLifecycleChecks.state(maid),demand,planning,heading,new dev.winefox.skyfarer.safety.CorridorProbe(maid))!=2)continue;started=true;require(provider.virtual(),"consumable selected before virtual");}
            else if(!provider.tick(maid,EquipmentLifecycleChecks.state(maid),planning,heading))break;
            int pushes=maid.pushCalls;provider.tick(maid,EquipmentLifecycleChecks.state(maid),planning,heading);require(maid.pushCalls==pushes,"same-tick force stacked");
            EquipmentLifecycleChecks.move(maid,FlightPrediction.step(EquipmentLifecycleChecks.state(maid),maid.yaw,maid.pitch,180,90,90,.08,false));
        }
        require(started&&provider.completed()&&maid.pushCalls>0&&maid.inventory.consumed==0&&maid.offhand.damage==0&&world.rockets.isEmpty(),"virtual execution/cutoff/source priority failed "+provider.detail(maid));
        provider.close(maid,"TEST",false);planning.close();
        System.out.println("Actual provider: certified pulse and recovery, one force/tick, no rockets/trident wear, bounded cutoff passed");
    }
    static void replay(boolean delayed)throws Exception {replay(delayed,false);}
    static void replay(boolean delayed,boolean mixed)throws Exception {
        SkyfarerSettings.reset("all");SkyfarerSettings.setDebugLogging(true);ReplayWorld.events.clear();
        System.setProperty("wfs.replayTickMillis",System.getProperty("wfs.cometTickMillis",delayed?"5":"15"));
        var world=new ReplayWorld();world.loadedRadius=256;var maid=new EntityMaid(world,Vec3.ZERO);maid.owner=new Player(world,new Vec3(8,0,8));
        maid.baubles.setStackInSlot(0,eternal());maid.inventory.count=mixed?64:0;
        if(mixed){maid.baubles.setStackInSlot(1,ordinary());maid.rain=true;maid.offhand=new ItemStack(Items.TRIDENT);maid.offhand.strength=3;}
        var target=new LivingEntity(world,new Vec3(0,0,24));
        var controller=new AirCombatController(maid);int hit=-1,virtualTicks=0,heldAt=-1;boolean complete=false,collision=false;AutoCloseable block=null;
        var rows=new ArrayList<String>();
        try {
            for(int tick=1;tick<=500;tick++){
                world.advance(tick);controller.tick();if(controller.virtualPropulsionActive())virtualTicks++;
                if(delayed&&virtualTicks>=3&&heldAt<0){block=TrajectoryContinuityChecks.blockWorkers();heldAt=tick;}
                if(block!=null&&tick-heldAt>=16){block.close();block=null;System.setProperty("wfs.replayTickMillis",System.getProperty("wfs.cometTickMillis","15"));}
                require(!(controller.virtualPropulsionActive()&&controller.riptideActive())&&world.rockets.isEmpty(),"multiple active sources");
                require(maid.inventory.consumed==0&&maid.offhand.damage==0&&maid.random.draws==0,"consumable escaped exclusive policy");
                if(hit<0&&controller.spearAttackActive()&&maid.using&&maid.usedTicks>=10){
                    var aim=new FlightPrediction.Aim(target.getX(),-3,target.getZ(),.3,7.8,0,0,0,0,2.25,.1,0);
                    if(maid.velocity.length()>=.2&&aim.missSquared(EquipmentLifecycleChecks.state(maid),0)<1e-8){controller.recordSpearHit(target.id);target.alive=false;hit=tick;}
                }
                if(hit>0&&tick>hit+30&&controller.state()!=AirCombatState.ATTACK_EXIT){complete=true;break;}
                if(maid.using)maid.usedTicks++;
                if(maid.glide){var s=EquipmentLifecycleChecks.state(maid);var next=FlightPrediction.step(s,maid.yaw,maid.pitch,180,90,90,.08,false);
                    if(controller.state()==AirCombatState.LANDING&&next.y()-maid.getEyeHeight()<=0&&hit>0){complete=true;break;}
                    if(!new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(s,next,0,0)){collision=true;break;}
                    EquipmentLifecycleChecks.move(maid,next);
                } else if(!maid.ground){maid.pos=maid.pos.add(maid.velocity);maid.velocity=new Vec3(maid.velocity.x*.91,(maid.velocity.y-.08)*.98,maid.velocity.z*.91);}
                if(maid.getY()<=0){maid.pos=new Vec3(maid.getX(),0,maid.getZ());maid.ground=true;maid.glide=false;maid.pose=Pose.STANDING;maid.velocity=Vec3.ZERO;}
                rows.add(tick+"\t"+controller.state()+"\t"+maid.pos+"\t"+maid.velocity+"\t"+controller.debugSnapshot().getString("reason"));
            }
        } finally {
            if(block!=null)block.close();var dir=java.nio.file.Path.of("build/reports/comet");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve((mixed?"authority-":"virtual-")+System.getProperty("wfs.cometTickMillis","normal")+"-"+delayed+".tsv"),rows);java.nio.file.Files.write(dir.resolve((mixed?"authority-":"virtual-")+System.getProperty("wfs.cometTickMillis","normal")+"-"+delayed+".events.log"),ReplayWorld.events);
            controller.dispose("TEST",false);PlannerSession.stop();
        }
        String summary="virtual controller mixed="+mixed+" delayed="+delayed+" guidedTicks="+virtualTicks+" hit="+hit+" exit="+complete+" collision="+collision+" hold="+heldAt;
        System.out.println(summary);require(virtualTicks>0&&hit>0&&complete&&!collision&&maid.inventory.consumed==0,summary);
    }
    static void preemption(){
        for(String cause:List.of("equipment","safety","wall")){
            var world=new ReplayWorld();var maid=RiptideReleaseChecks.maid(world);maid.baubles.setStackInSlot(0,eternal());
            var provider=new FlightPropulsion();var planning=new AsyncPlanning(maid);var heading=new HeadingController();int tick;
            for(tick=1;tick<100&&!provider.virtual();tick++){
                world.advance(tick);planning.begin(tick,"PREEMPT",null,0);heading.beginTick(maid);provider.prepare(maid,true);
                provider.prefer(maid,EquipmentLifecycleChecks.state(maid),new PropulsionDemand(PropulsionDemand.Phase.AIR_RECOVERY,0,85,100,.9),planning,heading,new dev.winefox.skyfarer.safety.CorridorProbe(maid));
            }
            require(provider.virtual(),"no active pulse for preemption "+cause);
            world.advance(tick);planning.begin(tick,"PREEMPT",null,0);heading.beginTick(maid);
            if(cause.equals("equipment"))maid.baubles.setStackInSlot(0,ItemStack.EMPTY);
            if(cause.equals("safety"))heading.steerAngles(maid,90,-20,45,20,HeadingController.Owner.SAFETY);
            if(cause.equals("wall"))world.boxes.add(new FlightCorridor.Box(-10,79,-10,10,83,10));
            var velocity=maid.velocity;int pushes=maid.pushCalls;
            require(!provider.tick(maid,EquipmentLifecycleChecks.state(maid),planning,heading)&&!provider.virtual()&&maid.pushCalls==pushes&&maid.velocity.equals(velocity),"preemption added force or zeroed inertia: "+cause);
            provider.close(maid,"TEST",false);planning.close();
        }
        System.out.println("Equipment removal, higher-priority steering and a new live obstacle immediately stop virtual thrust while retaining inertia");
    }
    static void squad(){
        var world=new ReplayWorld();var maids=new ArrayList<EntityMaid>();var providers=new ArrayList<FlightPropulsion>();var planners=new ArrayList<AsyncPlanning>();var headings=new ArrayList<HeadingController>();
        int count=8;var selected=new boolean[count];
        for(int i=0;i<count;i++){var maid=RiptideReleaseChecks.maid(world);maid.pos=new Vec3(i*2,80,0);maid.baubles.setStackInSlot(0,eternal());maids.add(maid);providers.add(new FlightPropulsion());planners.add(new AsyncPlanning(maid));headings.add(new HeadingController());}
        int accepted=0;
        for(int tick=1;tick<=120&&accepted<count;tick++){
            world.advance(tick);
            for(int i=0;i<count;i++)if(!selected[i]){
                var maid=maids.get(i);var planning=planners.get(i);var provider=providers.get(i);var heading=headings.get(i);
                planning.begin(tick,"SQUAD",null,0);heading.beginTick(maid);provider.prepare(maid,true);
                int result=provider.prefer(maid,EquipmentLifecycleChecks.state(maid),new PropulsionDemand(PropulsionDemand.Phase.AIR_RECOVERY,maid.getX(),85,100,.9),planning,heading,new dev.winefox.skyfarer.safety.CorridorProbe(maid));
                require(planning.cells()<=768&&planning.pending()<=2,"unbounded per-maid work");
                if(result==2){require(provider.virtual(),"squad consumed another provider");selected[i]=true;accepted++;provider.close(maid,"TEST",false);planning.close();}
            }
        }
        for(int i=0;i<count;i++){providers.get(i).close(maids.get(i),"TEST",false);planners.get(i).close();}
        require(accepted==count&&world.rockets.isEmpty(),"squad starved or spawned rockets: "+accepted);
        System.out.println("Eight virtual-equipped maids obtained certified schedules within shared worker/capture bounds; zero propulsion entities");
    }
}
