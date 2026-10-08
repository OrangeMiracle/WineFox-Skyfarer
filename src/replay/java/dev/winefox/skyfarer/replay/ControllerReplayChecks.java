package dev.winefox.skyfarer.replay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.config.*;
import dev.winefox.skyfarer.compat.spears.SpearCompat;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Runs the production AirCombatController tick, real selectors, planners, settings and firework manager.
 * Only external engine APIs are fixtures. State transitions are never assigned by the test. */
public final class ControllerReplayChecks {
    private static final List<String> failures=new ArrayList<>();
    public static void main(String[] args) throws Exception {
        acquisitionChecks();
        occlusionChecks();
        run("flat-near",8,0,1,5,false,1);
        run("flat-normal",20,0,1,5,false,1);
        run("flat-behind-long-pulse",12,180,2,11,false,1);
        run("home-continuous",22,90,1,0,true,3);
        run("flat-short-pulse",20,-90,0,0,false,1);
        run("flat-longest-pulse",20,180,3,11,false,1);
        run("flat-far",40,0,1,11,false,1);
        run("follow-continuous",18,-90,1,5,false,3);
        run("slope-cycle",20,0,1,5,false,1);
        run("small-target-cycle",20,0,1,5,false,1);
        run("target-despawn",20,0,1,5,false,1);
        run("task-cancel",20,0,1,5,false,1);
        run("snow-layer-cycle",20,0,1,5,false,1);
        run("mixed-partial-cycle",20,0,1,5,false,1);
        run("slab-cycle",20,0,1,5,false,1);
        run("ice-inertia-cycle",20,90,1,5,false,1);
        run("radius-32-cycle",20,0,1,5,false,1);
        run("surface-and-cave",20,0,1,5,false,1);
        run("summit-cycle",28,0,1,5,false,1);
        run("uphill-cycle",28,0,1,5,false,1);
        run("upward-pillar",32,0,1,5,false,1);
        dev.winefox.skyfarer.async.PlannerSession.stop();
        if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures));
        System.out.println("Production controller full lifecycle replay passed: acquire -> takeoff -> approach -> attack/hit -> exit -> reacquire -> return -> landing. No game launched.");
    }
    private static void run(String name,double targetZ,float yaw,int duration,int jitter,boolean home,int enemies) throws Exception {
        String filter=System.getProperty("wfs.replayCase","");if(!filter.isEmpty()&&!filter.equals(name))return;
        SkyfarerSettings.reset("all"); SkyfarerSettings.setDebugLogging(true);
        var world=new ReplayWorld();world.rocketJitter=jitter;ReplayWorld.events.clear();
        if(name.equals("radius-32-cycle"))SkyfarerSettings.set("slope_sample_radius",32);
        if(name.equals("snow-layer-cycle")||name.equals("slab-cycle"))
            world.boxes.add(new FlightCorridor.Box(-128,0,-128,128,name.equals("slab-cycle")?.5:.125,128));
        if(name.equals("mixed-partial-cycle"))for(int z=-32;z<80;z+=2)
            world.boxes.add(new FlightCorridor.Box(-96,0,z,96,new double[]{.125,.375,.5}[Math.floorMod(z/2,3)],z+2));
        if(name.equals("slope-cycle"))for(int z=12;z<38;z+=2)
            world.boxes.add(new FlightCorridor.Box(-12,0,z,12,(z-10)*.25,z+2));
        if(name.equals("summit-cycle"))world.boxes.add(new FlightCorridor.Box(-.5,0,targetZ-.5,.5,8,targetZ+.5));
        if(name.equals("upward-pillar"))world.boxes.add(new FlightCorridor.Box(-.5,0,targetZ-.5,.5,18,targetZ+.5));
        if(name.equals("uphill-cycle"))for(int z=4;z<50;z++)world.boxes.add(new FlightCorridor.Box(-12,0,z,12,(z-3)*.8,z+1));
        var origin=new Vec3(0,world.ground(0,0,10),0);
        var maid=new EntityMaid(world,origin);maid.owner=new Player(world,origin);maid.yaw=yaw;maid.home=home;maid.inventory.duration=duration;
        double operatingHeight=origin.y;
        if(name.equals("surface-and-cave"))new LivingEntity(world,new Vec3(0,-6,12));
        for(int i=0;i<enemies;i++) {
            var target=new LivingEntity(world,new Vec3(i*3,world.ground(i*3,targetZ+i*4,20),targetZ+i*4));
            operatingHeight=Math.max(operatingHeight,target.getY());
            if(name.equals("small-target-cycle"))target.height=.5f;
        }
        boolean interrupted=name.equals("target-despawn")||name.equals("task-cancel"),interruptionSent=false;
        int expectedHits=interrupted?0:enemies;
        var controller=new AirCombatController(maid);
        var trace=new ArrayList<String>();trace.add("tick\tstate\tx\ty\tz\tvx\tvy\tvz\tpitch\tfuel\thits\treason");
        var visited=EnumSet.noneOf(AirCombatState.class);int hits=0,firstAttack=-1,landTick=-1,lastHit=0,eventIndex=0,landingFuel=-1;double maxHeight=0,maxRadius=0;String failure="";
        for(int tick=0;tick<1200;tick++) {
            world.advance(tick+1);
            // External surface inertia just before native jumping (idle fixture ticks are stationary).
            if(name.equals("ice-inertia-cycle") && controller.state()==AirCombatState.TAKEOFF_PREP && maid.ground)
                maid.velocity=new Vec3(.18,0,-.08);
            controller.tick();
            var state=controller.state(); visited.add(state);
            if(landingFuel>=0&&hits<expectedHits&&maid.task&&maid.glide&&!maid.ground
                &&(state==AirCombatState.APPROACH||state==AirCombatState.REPOSITION||state.locksRunTarget())
                &&world.entities.stream().anyMatch(t->t.id.equals(controller.debugTarget())&&TargetEvaluator.valid(maid,t,true,origin)&&TargetEvaluator.visible(maid,t))){
                // 0.6.2.9 permits qualified soft-return recovery before touchdown. A completed or
                // cancelled mission still cannot restart; the complete hit/exit/landing checks remain.
                landingFuel=-1;
            }
            if(landingFuel>=0 && (state!=AirCombatState.LANDING && state!=AirCombatState.GROUNDED || maid.inventory.consumed!=landingFuel)) {
                failure="landing restarted combat/boosting";break;
            }
            if(state==AirCombatState.LANDING) {
                landingFuel=maid.inventory.consumed;
                if(maid.using){failure="landing kept spear charge";break;}
            }
            // A valid handoff can enter and leave a state in the same tick; retain both transitions.
            while(eventIndex<ReplayWorld.events.size()) {
                String event=ReplayWorld.events.get(eventIndex++);
                for(var value:AirCombatState.values())if(event.contains(" -> "+value+";"))visited.add(value);
            }
            if(controller.spearAttackActive() && firstAttack<0)firstAttack=tick;
            if(interrupted&&!interruptionSent&&(state==AirCombatState.DIVE||state==AirCombatState.SPEAR_CHARGE)) {
                if(name.equals("task-cancel"))maid.task=false;
                else for(var e:world.entities)if(e.hostile)e.alive=false;
                interruptionSent=true;
            }
            if(maid.using) {
                if(maid.usedTicks>=SpearCompat.delayTicks(maid.weapon) && controller.spearAttackActive()) {
                    for(var target:new ArrayList<>(world.entities))if(target.hostile&&target.alive&&!controller.alreadyPierced(target.id)) {
                        double expansion=SkyfarerSettings.get("spear_vertical_expansion");
                        var aim=new FlightPrediction.Aim(target.getX(),target.getY()-expansion,target.getZ(),target.getBbWidth()/2,
                            target.getBbHeight()+expansion*2,0,0,0,0,2.25,.1,0);
                        if(maid.velocity.length()>=.2 && aim.missSquared(state(maid),0)<1e-8 && world.ray(maid.getEyePosition(),target.getBoundingBox().getCenter())) {
                            controller.recordSpearHit(target.id);target.alive=false;hits++;lastHit=tick;
                        }
                    }
                }
                maid.usedTicks++;
            }
            boolean powered=world.rockets.stream().anyMatch(r->r.alive&&r.createdAt<world.time);
            Vec3 next;
            if(maid.glide) {
                var s=state(maid);var moved=FlightPrediction.step(s,maid.yaw,maid.pitch,180,90,90,.08,powered);
                next=new Vec3(moved.x(),moved.y()-maid.getEyeHeight(),moved.z());maid.velocity=new Vec3(moved.vx(),moved.vy(),moved.vz());
            } else if(!maid.ground) { next=maid.pos.add(maid.velocity);maid.velocity=new Vec3(maid.velocity.x*.91,(maid.velocity.y-.08)*.98,maid.velocity.z*.91); }
            else next=maid.pos;
            double ground=world.ground(next.x,next.z,Math.max(maid.pos.y,next.y)+.1);
            if(maid.glide && state!=AirCombatState.LANDING && !new dev.winefox.skyfarer.safety.CorridorProbe(maid).clear(
                maid.getEyePosition(),next.add(0,maid.getEyeHeight(),0),0,0)) {failure="body corridor collision during "+state;break;}
            if(next.y<=ground) {next=new Vec3(next.x,ground,next.z);maid.ground=true;maid.glide=false;maid.velocity=Vec3.ZERO;}
            maid.pos=next;
            for(var r:world.rockets)if(r.alive&&r.createdAt<world.time&&++r.life>r.lifetime)r.discard();
            maxHeight=Math.max(maxHeight,maid.getY());maxRadius=Math.max(maxRadius,maid.pos.horizontalDistance());
            var debug=controller.debugSnapshot();
            trace.add(String.format(Locale.ROOT,"%d\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.2f\t%d\t%d\t%s",tick,state,maid.getX(),maid.getY(),maid.getZ(),maid.velocity.x,maid.velocity.y,maid.velocity.z,maid.pitch,maid.inventory.count,hits,debug.getString("reason")));
            if(maxHeight-operatingHeight>55){failure="runaway altitude "+maxHeight+" above operating height "+operatingHeight;break;}
            if(maxRadius>100){failure="escaped battle area "+maxRadius;break;}
            if(maid.ground && flightEstablished(visited) && state!=AirCombatState.LANDING && state!=AirCombatState.GROUNDED) {failure="unplanned ground contact during "+state;break;}
            if(tick-lastHit>450&&hits<expectedHits){failure="no hit/progress for 450 ticks";break;}
            if(tick>400&&firstAttack<0){failure="no attack within 400 ticks; state="+state;break;}
            if(maid.teleports>0){failure="normal lifecycle relied on emergency teleport";break;}
            if(hits==expectedHits&&state==AirCombatState.GROUNDED&&maid.ground&&flightEstablished(visited)){landTick=tick;break;}
        }
        if(failure.isEmpty() && (hits!=expectedHits||landTick<0||!visited.contains(AirCombatState.LANDING)||!interrupted&&!visited.contains(AirCombatState.ATTACK_EXIT)))
            failure="incomplete lifecycle; hits="+hits+" landing="+landTick+" states="+visited;
        Path dir=Path.of("build/reports/controller-replay");Files.createDirectories(dir);Files.write(dir.resolve(name+".tsv"),trace);Files.write(dir.resolve(name+".events.log"),ReplayWorld.events);
        System.out.printf(Locale.ROOT,"%s attack=%d hits=%d landed=%d peak=%.2f radius=%.2f rockets=%d%n",name,firstAttack,hits,landTick,maxHeight,maxRadius,maid.inventory.consumed);
        controller.dispose("REPLAY_FINISHED",true);
        if(!failure.isEmpty())failures.add(name+": "+failure+"; trace="+dir.resolve(name+".tsv"));
    }
    private static FlightPrediction.State state(EntityMaid m){return new FlightPrediction.State(m.getX(),m.getEyeY(),m.getZ(),m.velocity.x,m.velocity.y,m.velocity.z,m.yaw,m.pitch);}
    private static boolean flightEstablished(Set<AirCombatState> states){
        // A certified Launch Attack goes directly from TAKEOFF to ATTACK_RUN without CLIMB.
        return states.contains(AirCombatState.CLIMB)||states.contains(AirCombatState.ATTACK_RUN);
    }
    private static void occlusionChecks() throws Exception {
        SkyfarerSettings.reset("all");var world=new ReplayWorld();
        var maid=new EntityMaid(world,new Vec3(0,10,0));maid.owner=new Player(world,Vec3.ZERO);
        maid.ground=false;maid.glide=true;maid.pose=Pose.FALL_FLYING;
        var target=new LivingEntity(world,new Vec3(0,10,20));var controller=new AirCombatController(maid);
        controller.acceptCommand(target);
        var scan=AirCombatController.class.getDeclaredMethod("scan");scan.setAccessible(true);scan.invoke(controller);
        // Following a visible target must refresh observation without forcing a target rescan.
        long seen=SkyfarerSettings.ticks("loiter_time_ticks")+10;world.time=seen;
        var motion=AirCombatController.class.getDeclaredMethod("sampleTargetMotion");motion.setAccessible(true);motion.invoke(controller);
        world.boxes.add(new FlightCorridor.Box(-20,0,8,20,30,9));scan.invoke(controller);
        var approach=AirCombatController.class.getDeclaredMethod("startApproach");approach.setAccessible(true);approach.invoke(controller);
        var recent=AirCombatController.class.getDeclaredMethod("recentOccludedTarget");recent.setAccessible(true);
        if(controller.state()!=AirCombatState.LOITER_SEARCH||controller.debugTarget()!=null||!(boolean)recent.invoke(controller))
            throw new AssertionError("temporary occlusion must preserve bounded search, not an attack target");
        world.time=seen+SkyfarerSettings.ticks("loiter_time_ticks");
        if((boolean)recent.invoke(controller))throw new AssertionError("occluded search never expires");
        world.time=seen;target.alive=false;
        if((boolean)recent.invoke(controller))throw new AssertionError("dead target retained for search");
        controller.dispose("REPLAY_OCCLUSION_FINISHED",true);
        System.out.println("Visible -> occluded search, hidden attack exclusion, expiry and dead-target release passed.");
    }
    private static void acquisitionChecks() {
        SkyfarerSettings.reset("all");
        var world=new ReplayWorld();world.boxes.clear();
        world.boxes.add(new FlightCorridor.Box(-128,-1,-128,128,0,128));
        world.boxes.add(new FlightCorridor.Box(-128,-10,-128,128,-6,128));
        var maid=new EntityMaid(world,Vec3.ZERO);maid.owner=new Player(world,Vec3.ZERO);
        var cave=new LivingEntity(world,new Vec3(0,-6,12));
        if(TargetEvaluator.scan(maid,cave,cave.id,Vec3.ZERO).selection().target()!=null)
            throw new AssertionError("hidden cave target retained by previous/preferred priority");
        var controller=new AirCombatController(maid);
        for(int tick=0;tick<120;tick++){world.advance(tick);controller.tick();if(controller.controlsAir()||maid.inventory.consumed!=0)throw new AssertionError("cave-only world launched maid");}
        var surface=new LivingEntity(world,new Vec3(0,0,12));
        world.boxes.add(new FlightCorridor.Box(-1,0,10,1,1.35,11));
        if(!TargetEvaluator.visible(maid,surface))throw new AssertionError("visible upper body must remain selectable above partial cover");
        if(TargetEvaluator.scan(maid,cave,cave.id,Vec3.ZERO).selection().target()!=surface)throw new AssertionError("surface target should win over cave");
        for(double offset:new double[]{0,.5,1}) {
            SkyfarerSettings.set("attack_aim_offset",offset);
            if(Math.abs(TargetEvaluator.aimPoint(surface).y-surface.getBoundingBox().getCenter().y-offset)>1e-8)throw new AssertionError("live aim offset");
        }
        var friendly=new LivingEntity(world,new Vec3(3,0,12));friendly.hostile=false;
        if(TargetEvaluator.attackable(maid,friendly))throw new AssertionError("unmarked friendly became attackable");
        CommandAccess.designated=(m,t)->m==maid&&t==friendly;
        if(!TargetEvaluator.attackable(maid,friendly))throw new AssertionError("explicit friendly mark still blocked by native ally/canAttack rules");
        if(TargetEvaluator.attackable(maid,maid.owner)||TargetEvaluator.attackable(maid,maid))throw new AssertionError("mark override bypassed owner/self exclusion");
        CommandAccess.designated=(m,t)->false;
        if(TargetEvaluator.attackable(maid,friendly))throw new AssertionError("unmark did not restore friendly protection");
        controller.dispose("REPLAY_ACQUISITION_FINISHED",true);
        System.out.println("Cave-only idle, hidden preferred rejection, visible upper body and live aim offsets passed.");
    }
}
