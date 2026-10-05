package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.TlmFollowCompat;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.mixin.FireworkLifetimeAccessor;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Free dispatch propulsion exists only outside the combat handoff. No inventory writes. */
final class TransitFlight {
    private final HeadingController heading=new HeadingController();
    private FireworkRocketEntity rocket;
    private SurfaceLaunchPlanner.Plan launch;
    private int ticks,departure,waterTicks;
    private GlidePlanner.Plan cached;
    private GlidePlanner.Request lastRequest;
    private EntityMaid swimmer;
    private boolean previousSwimming;
    private boolean descending;private double cruiseY=Double.NaN;private CreativeTransitPlanner.Motion fastMotion;
    private int blockedFastTicks;
    private dev.winefox.skyfarer.async.AsyncPlanning planning;
    boolean waterEscape(){return swimmer!=null;}
    String diagnostics(){return "launch="+launch+" departure="+departure+" plan="+cached+" rocket="+remaining()+" jobs="+(planning==null?0:planning.pending());}
    boolean tick(EntityMaid maid,Vec3 target) {
        return tickFlight(maid,target,false);
    }
    boolean tick(EntityMaid maid,Vec3 target,boolean creative) {
        if(planning==null)planning=new dev.winefox.skyfarer.async.AsyncPlanning(maid);
        planning.begin(maid.level().getGameTime(),"transit:"+creative+":"+maid.isInWaterOrBubble(),
            net.minecraft.core.BlockPos.containing(target),dev.winefox.skyfarer.config.SkyfarerSettings.revision());
        try{return tickFlight(maid,target,creative);}
        catch(dev.winefox.skyfarer.async.PlannerSession.Pending wait){
            if(maid.onGround()){ticks=Math.max(0,ticks-1);return true;}
            var v=maid.getDeltaMovement();var s=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
            var space=new CorridorProbe(maid,maid.getBbWidth(),maid.getBbHeight(),maid.getEyeHeight(),maid.isInWaterOrBubble());
            for(double pitch:new double[]{maid.getXRot(),-35,-65,-88}){
                var next=SurfaceLaunchPlanner.step(s,maid.getYRot(),pitch,maid.getAttributeValue(Attributes.GRAVITY),remaining()>0,maid.isInWaterOrBubble());
                if(space.clear(s,next,.02,.01)){heading.steerAngles(maid,maid.getYRot(),pitch,180,90);break;}
            }
            return true;
        }
    }
    private boolean tickFlight(EntityMaid maid,Vec3 target,boolean creative) {
        ticks++;heading.beginTick(maid);TlmFollowCompat.clearGroundMovement(maid);
        double gravity=maid.getAttributeValue(Attributes.GRAVITY);
        Vec3 v=maid.getDeltaMovement(),toward=target.subtract(maid.position());
        double yaw=Math.toDegrees(Math.atan2(-toward.x,toward.z));
        if(maid.isInWaterOrBubble())return escapeWater(maid,yaw,gravity);
        restoreSwimming();
        if(maid.onGround()) {
            if(ticks>80)return false;
            if(!new CorridorProbe(maid).segment(maid.getEyePosition().add(0,.01,0),maid.getEyePosition().add(0,.45,0),.02,0))return false;
            var jump=new FlightPrediction.State(maid.getX(),maid.getY()+.82,maid.getZ(),v.x,.33,v.z,maid.getYRot(),maid.getXRot());
            launch=planning.launch(jump,yaw,maid.getY()+7,gravity,0,new CorridorProbe(maid,maid.getBbWidth(),.6,.4),SurfaceLaunchPlanner.DRY);
            if(launch==null)return false;
            heading.steerAngles(maid,launch.yaw(),launch.pitch(),180,90);maid.getJumpControl().jump();return true;
        }
        if(!maid.isFallFlying()&&!ElytraFlightController.startGliding(maid))return false;
        maid.setPose(Pose.FALL_FLYING);
        var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
        var space=new CorridorProbe(maid);
        if(launch!=null && departure==0) {
            launch=planning.launch(start,yaw,launch.height(),gravity,0,space,SurfaceLaunchPlanner.DRY);
            if(launch==null)return false;
            if(!space.clear(start,SurfaceLaunchPlanner.step(start,launch.yaw(),launch.pitch(),gravity,true,false),.04,.01))return false;
            if(launch==null)return false;
            heading.steerAngles(maid,launch.yaw(),launch.pitch(),180,90);boost(maid);departure=1;return true;
        }
        if(launch!=null && departure<6) {
            double pitch=SurfaceLaunchPlanner.command(start,launch,departure++,gravity,remaining()>0);
            var next=SurfaceLaunchPlanner.step(start,launch.yaw(),pitch,gravity,remaining()>0,false);
            if(!space.clear(start,next,.04,.01))launch=null;
            else {heading.steerAngles(maid,launch.yaw(),pitch,180,90);return true;}
        }
        launch=null;
        if(creative)return creativeTravel(maid,target,start,space,gravity);
        var request=new GlidePlanner.Request(yaw,target.y+10,0,0,14,Double.NaN,remaining(),5,3,8,gravity,.25,.25,false,null);
        // Only local receding-horizon prediction, no global pathfinding along the entire journey.
        if(cached==null || !cached.safe() || ticks%5==0)cached=planning.glide(start,request,space);
        var next=FlightPrediction.step(start,cached.yaw(),cached.pitch(),cached.yawRate(),cached.pitchRate(),cached.pitchRate(),gravity,remaining()>0);
        if(!space.clear(start,next,.04,.04))cached=planning.glide(start,request,space);
        if(remaining()==0 && (v.horizontalDistance()<1 || !cached.safe())) {
            if(Double.isFinite(planning.pulseCost(start,request,space,0,.75,.02))) {
                boost(maid);request=request.withPulse(remaining());cached=planning.glide(start,request,space);
            }
        }
        lastRequest=request;if(!cached.safe()&&!planning.waiting())return false;
        heading.steerAngles(maid,cached.yaw(),cached.pitch(),cached.yawRate(),cached.pitchRate());return true;
    }
    private boolean creativeTravel(EntityMaid maid,Vec3 target,FlightPrediction.State start,CorridorProbe space,double gravity){
        if(rocket!=null){rocket.discard();rocket=null;}
        double horizontal=target.subtract(maid.position()).horizontalDistance();
        if(Double.isNaN(cruiseY)){
            double ceiling=maid.level().getMaxBuildHeight()-16;
            double requested=maid.level().dimensionType().hasCeiling()?Math.max(start.y(),target.y)+12:dev.winefox.skyfarer.config.SkyfarerSettings.get("creative_transit_height");
            cruiseY=CreativeTransitPlanner.cruiseHeight(start.y(),target.y,horizontal,requested,ceiling);
        }
        descending|=horizontal<=CreativeTransitPlanner.DIVE_HORIZONTAL+.5;
        Vec3 goal=new Vec3(target.x,descending?target.y+1.4:cruiseY,target.z);
        fastMotion=CreativeTransitPlanner.choose(start,goal.x,goal.y,goal.z,maid.position().distanceTo(target),horizontal,descending,
            dev.winefox.skyfarer.config.SkyfarerSettings.get("creative_transit_speed_multiplier"),gravity,
            (a,b,side,vertical)->space.segment(new Vec3(a.x(),a.y(),a.z()),new Vec3(b.x(),b.y(),b.z()),side,vertical));
        if(fastMotion==null){
            // Return to local native guidance at ordinary speed if terrain has no direct corridor.
            Vec3 v=maid.getDeltaMovement();if(v.length()>1)maid.setDeltaMovement(v.normalize());
            cached=null;if(++blockedFastTicks>100)return false;
            return tickFlight(maid,target,false);
        }
        blockedFastTicks=0;
        heading.steerAngles(maid,fastMotion.yaw(),fastMotion.pitch(),180,90);
        var motion=fastMotion.input();maid.setDeltaMovement(new Vec3(motion.vx(),motion.vy(),motion.vz()));
        return true;
    }
    void handoff(EntityMaid maid){
        close();var v=maid.getDeltaMovement();if(v.length()>1.7)maid.setDeltaMovement(v.normalize().scale(1.7));
    }
    private boolean wet(EntityMaid maid,FlightPrediction.State state){var pos=net.minecraft.core.BlockPos.containing(state.x(),state.y()-.35,state.z());return maid.level().hasChunkAt(pos)&&maid.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);}
    private boolean escapeWater(EntityMaid maid,double yaw,double gravity) {
        if(swimmer==null){swimmer=maid;previousSwimming=maid.getSwimManager().wantToSwim();maid.getSwimManager().setWantToSwim(true);launch=null;departure=0;}
        if(++waterTicks>100)return false;
        if(maid.onGround())maid.getJumpControl().jump();
        if(!ElytraFlightController.startGliding(maid,true))return true;
        var v=maid.getDeltaMovement();var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
        var space=new CorridorProbe(maid,maid.getBbWidth(),.6,.4,true);
        double height=start.y()+4;
        for(int up=0;up<32;up++){var pos=maid.blockPosition().above(up);if(!maid.level().hasChunkAt(pos))break;if(!maid.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)){height=pos.getY()+4;break;}}
        if(launch==null)launch=remaining()>0?planning.launchRemaining(start,yaw,height,gravity,remaining(),space,s->wet(maid,s)):
            planning.launch(start,yaw,height,gravity,0,space,s->wet(maid,s));
        if(launch==null)return false;
        double pitch=SurfaceLaunchPlanner.command(start,launch,departure++,gravity,true);
        if(!space.clear(start,SurfaceLaunchPlanner.step(start,launch.yaw(),pitch,gravity,true,true),.04,.01))return false;
        if(remaining()==0){if(!Double.isFinite(planning.launchPulse(start,launch,gravity,0,space,s->wet(maid,s))))return false;boost(maid);}
        heading.steerAngles(maid,launch.yaw(),pitch,180,90);return true;
    }
    void debug(net.minecraft.nbt.CompoundTag data,EntityMaid maid) {
        var v=maid.getDeltaMovement();var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
        var space=new CorridorProbe(maid,maid.getBbWidth(),.6,.4,waterEscape());double gravity=maid.getAttributeValue(Attributes.GRAVITY);
        if(fastMotion!=null){
            var path=new java.util.ArrayList<GlidePlanner.TracePoint>();var to=fastMotion.next();int count=Math.max(1,(int)Math.ceil(new Vec3(to.x(),to.y(),to.z()).distanceTo(maid.getEyePosition())/2));
            for(int i=0;i<=count;i++){var p=maid.getEyePosition().lerp(new Vec3(to.x(),to.y(),to.z()),i/(double)count);path.add(new GlidePlanner.TracePoint(new FlightPrediction.State(p.x,p.y,p.z,0,0,0,to.yaw(),to.pitch()),.15,.12));}
            dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,path);
            data.putString("reason",(descending?"CREATIVE_DESCENT":"CREATIVE_HIGH_CRUISE")+" effective multiplier="+fastMotion.multiplier());
        }
        else if(planning!=null)dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,planning.trace());
    }
    private void boost(EntityMaid maid){
        if(remaining()>0)return;
        var stack=new ItemStack(Items.FIREWORK_ROCKET);
        // The vanilla item defaults to Flight 1. Explicit Flight 0 must match our short-pulse forecast.
        stack.set(net.minecraft.core.component.DataComponents.FIREWORKS,new net.minecraft.world.item.component.Fireworks(0,java.util.List.of()));
        rocket=new FireworkRocketEntity(maid.level(),stack,maid);maid.level().addFreshEntity(rocket);
    }
    private int remaining(){if(rocket==null||!rocket.isAlive())return 0;var life=(FireworkLifetimeAccessor)rocket;return Math.max(0,life.wfs$lifetime()-life.wfs$life()+1);}
    private void restoreSwimming(){if(swimmer!=null){swimmer.getSwimManager().setWantToSwim(previousSwimming);swimmer=null;launch=null;departure=0;waterTicks=0;cached=null;}}
    void close(){if(planning!=null)planning.close();if(rocket!=null)rocket.discard();rocket=null;restoreSwimming();}
}
