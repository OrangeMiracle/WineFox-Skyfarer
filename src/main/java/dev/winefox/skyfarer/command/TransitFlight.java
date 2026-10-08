package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.compat.tlm.TlmFollowCompat;
import dev.winefox.skyfarer.flight.*;
import dev.winefox.skyfarer.safety.CorridorProbe;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Dispatch prefers certified inventory Riptide; legacy free-rocket fallback stays outside combat. */
final class TransitFlight {
    private final dev.winefox.skyfarer.compat.tlm.FlightPropulsion propulsion=new dev.winefox.skyfarer.compat.tlm.FlightPropulsion(true);
    private final HeadingController heading=new HeadingController();
    private SurfaceLaunchPlanner.Plan launch;
    private int ticks,departure,waterTicks;
    private GlidePlanner.Plan cached;
    private EntityMaid swimmer;
    private boolean previousSwimming;
    private boolean descending;private double cruiseY=Double.NaN;private CreativeTransitPlanner.Motion fastMotion;
    private int blockedFastTicks;
    private dev.winefox.skyfarer.async.AsyncPlanning planning;
    private EntityMaid controlled;
    boolean waterEscape(){return swimmer!=null;}
    String diagnostics(){return "propulsion="+propulsion.reason()+" launch="+launch+" departure="+departure+" plan="+cached+" rocket="+remaining()+" jobs="+(planning==null?0:planning.pending());}
    boolean tick(EntityMaid maid,Vec3 target) {
        return tick(maid,target,false);
    }
    boolean tick(EntityMaid maid,Vec3 target,boolean creative) {
        controlled=maid;
        propulsion.prepare(maid,dev.winefox.skyfarer.compat.tlm.SkyfarerEquipment.elytra(maid));
        propulsion.context("TRANSIT");
        if(planning==null)planning=new dev.winefox.skyfarer.async.AsyncPlanning(maid);
        planning.begin(maid.level().getGameTime(),"transit:"+creative+":"+maid.isInWaterOrBubble(),
            net.minecraft.core.BlockPos.containing(target),dev.winefox.skyfarer.config.SkyfarerSettings.revision());
        try{return tickFlight(maid,target,creative);}
        catch(dev.winefox.skyfarer.async.PlannerSession.Pending wait){
            if(maid.onGround()){ticks=Math.max(0,ticks-1);return true;}
            var v=maid.getDeltaMovement();var s=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot(),planning.physics(),ignitionDelay(maid));
            var space=new CorridorProbe(maid,maid.getBbWidth(),maid.getBbHeight(),maid.getEyeHeight(),maid.isInWaterOrBubble());
            for(double pitch:new double[]{maid.getXRot(),-35,-65,-88}){
                var next=SurfaceLaunchPlanner.step(s,maid.getYRot(),pitch,planning.physics().gravity(),remaining()>0,maid.isInWaterOrBubble());
                if(space.clear(s,next,.02,.01)){heading.steerAngles(maid,maid.getYRot(),pitch,180,90);break;}
            }
            return true;
        }
    }
    private boolean tickFlight(EntityMaid maid,Vec3 target,boolean creative) {
        ticks++;heading.beginTick(maid);TlmFollowCompat.clearGroundMovement(maid);
        double gravity=planning.physics().gravity();
        Vec3 v=maid.getDeltaMovement(),toward=target.subtract(maid.position());
        double yaw=Math.toDegrees(Math.atan2(-toward.x,toward.z));
        if(propulsion.guided()){
            var state=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot(),planning.physics());
            if(propulsion.tick(maid,state,planning,heading))return true;
            cached=null;launch=null;departure=0;
        }
        if(maid.isInWaterOrBubble())return escapeWater(maid,yaw,gravity);
        restoreSwimming();
        if(maid.onGround()) {
            if(prefer(maid,target,true)>0)return true;
            if(ticks>80)return false;
            if(!new CorridorProbe(maid).segment(maid.getEyePosition().add(0,.01,0),maid.getEyePosition().add(0,.45,0),.02,0))return false;
            var jump=new FlightPrediction.State(maid.getX(),maid.getY()+.82,maid.getZ(),v.x,.33,v.z,maid.getYRot(),maid.getXRot(),planning.physics(),ignitionDelay(maid));
            launch=propulsion.launch(maid,jump,yaw,maid.getY()+7,planning,gravity,new CorridorProbe(maid,maid.getBbWidth(),.6,.4),SurfaceLaunchPlanner.DRY);
            heading.steerAngles(maid,launch.yaw(),launch.pitch(),180,90);maid.getJumpControl().jump();return true;
        }
        if(!maid.isFallFlying()&&!ElytraFlightController.startGliding(maid))return false;
        maid.setPose(Pose.FALL_FLYING);
        var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot(),planning.physics(),ignitionDelay(maid));
        var space=new CorridorProbe(maid);
        if(launch!=null && departure==0) {
            launch=propulsion.launch(maid,start,yaw,launch.height(),planning,gravity,space,SurfaceLaunchPlanner.DRY);
            if(launch==null)return false;
            if(!space.clear(start,SurfaceLaunchPlanner.step(start,launch.yaw(),launch.pitch(),gravity,true,false),.04,.01))return false;
            heading.steerAngles(maid,launch.yaw(),launch.pitch(),180,90);if(!propulsion.executeLaunch(maid,start,launch,true,planning,gravity,space,SurfaceLaunchPlanner.DRY))return false;departure=1;return true;
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
            var demand=new PropulsionDemand(PropulsionDemand.Phase.TRANSIT,target.x,Math.min(maid.getEyeY()+4,target.y+10),target.z,.9);
            var result=propulsion.request(maid,start,demand,true,new PropulsionRoute.Glide(request,.02,true,false,0),planning,heading,space);
            if(result.guided()||result.waiting())return true;
            if(result.attached()){request=request.withPulse(remaining());cached=planning.glide(start,request,space);}
        }
        if(!cached.safe()&&!planning.waiting())return false;
        heading.steerAngles(maid,cached.yaw(),cached.pitch(),cached.yawRate(),cached.pitchRate());return true;
    }
    private boolean creativeTravel(EntityMaid maid,Vec3 target,FlightPrediction.State start,CorridorProbe space,double gravity){
        propulsion.detach();
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
        audit(maid,"TRANSIT_TO_COMBAT");
        close(true);var v=maid.getDeltaMovement();if(v.length()>1.7)maid.setDeltaMovement(v.normalize().scale(1.7));
        if(!maid.onGround()&&!maid.isFallFlying())ElytraFlightController.startGliding(maid);
    }
    private boolean wet(EntityMaid maid,FlightPrediction.State state){var pos=net.minecraft.core.BlockPos.containing(state.x(),state.y()-.35,state.z());return maid.level().hasChunkAt(pos)&&maid.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);}
    private boolean escapeWater(EntityMaid maid,double yaw,double gravity) {
        if(swimmer==null){swimmer=maid;previousSwimming=maid.getSwimManager().wantToSwim();maid.getSwimManager().setWantToSwim(true);launch=null;departure=0;}
        if(++waterTicks>100)return false;
        if(maid.onGround())maid.getJumpControl().jump();
        if(!ElytraFlightController.startGliding(maid,true))return true;
        var v=maid.getDeltaMovement();var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot(),planning.physics(),ignitionDelay(maid));
        var space=new CorridorProbe(maid,maid.getBbWidth(),.6,.4,true);
        double height=start.y()+4;
        for(int up=0;up<32;up++){var pos=maid.blockPosition().above(up);if(!maid.level().hasChunkAt(pos))break;if(!maid.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)){height=pos.getY()+4;break;}}
        if(remaining()==0&&prefer(maid,new Vec3(start.x()-Math.sin(Math.toRadians(yaw))*64,height,start.z()+Math.cos(Math.toRadians(yaw))*64),false)>0)return true;
        if(launch==null)launch=remaining()>0?planning.launchRemaining(start,yaw,height,gravity,remaining(),space,s->wet(maid,s)):
            propulsion.launch(maid,start,yaw,height,planning,gravity,space,s->wet(maid,s));
        if(launch==null)return false;
        double pitch=SurfaceLaunchPlanner.command(start,launch,departure++,gravity,true);
        if(!space.clear(start,SurfaceLaunchPlanner.step(start,launch.yaw(),pitch,gravity,true,true),.04,.01))return false;
        if(remaining()==0&&!propulsion.executeLaunch(maid,start,launch,false,planning,gravity,space,s->wet(maid,s)))return false;
        heading.steerAngles(maid,launch.yaw(),pitch,180,90);return true;
    }
    void debug(net.minecraft.nbt.CompoundTag data,EntityMaid maid) {
        // A command can be assigned in ServerTick.Post before the first entity tick.
        // Debug is an observer: it must neither dereference an absent planner nor create one.
        if(planning==null){data.putString("reason","TRANSIT_WAITING_FIRST_TICK");return;}
        if(fastMotion!=null){
            var path=new java.util.ArrayList<GlidePlanner.TracePoint>();var to=fastMotion.next();int count=Math.max(1,(int)Math.ceil(new Vec3(to.x(),to.y(),to.z()).distanceTo(maid.getEyePosition())/2));
            for(int i=0;i<=count;i++){var p=maid.getEyePosition().lerp(new Vec3(to.x(),to.y(),to.z()),i/(double)count);path.add(new GlidePlanner.TracePoint(new FlightPrediction.State(p.x,p.y,p.z,0,0,0,to.yaw(),to.pitch()),.15,.12));}
            dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,path);
            data.putString("reason",(descending?"CREATIVE_DESCENT":"CREATIVE_HIGH_CRUISE")+" effective multiplier="+fastMotion.multiplier());
        }
        else if(planning!=null)dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,planning.trace());
    }
    private int prefer(EntityMaid maid,Vec3 target,boolean departure){
        var v=maid.getDeltaMovement();
        var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot(),planning.physics());
        var demand=new PropulsionDemand(departure?PropulsionDemand.Phase.TAKEOFF:PropulsionDemand.Phase.TRANSIT,target.x,
            departure?maid.getY()+5:Math.min(maid.getEyeY()+4,target.y+10),target.z,.9);
        return propulsion.prefer(maid,start,demand,planning,heading,new CorridorProbe(maid));
    }
    private int ignitionDelay(EntityMaid maid){return propulsion.projectionDelay(maid);}
    private int remaining(){return propulsion.projectedTicks();}
    private void restoreSwimming(){if(swimmer!=null){swimmer.getSwimManager().setWantToSwim(previousSwimming);swimmer=null;launch=null;departure=0;waterTicks=0;cached=null;}}
    private void audit(EntityMaid maid,String reason){
        if(dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging())dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] FLIGHT_LIFECYCLE maid={} action={} state=TRANSIT glide={} velocity={} altitude={} rocket={} order={}",
            maid.getUUID(),reason,maid.isFallFlying(),maid.getDeltaMovement(),maid.getY(),remaining(),dev.winefox.skyfarer.ai.CommandAccess.context.apply(maid));
    }
    void close(){close(false);}
    private void close(boolean handoff){if(controlled!=null){audit(controlled,"TRANSIT_CLOSE");propulsion.close(controlled,"TRANSIT_CLOSE_OR_HANDOFF",handoff);}controlled=null;if(planning!=null)planning.close();planning=null;cached=null;fastMotion=null;propulsion.detach();restoreSwimming();launch=null;ticks=departure=waterTicks=blockedFastTicks=0;descending=false;cruiseY=Double.NaN;}
}
