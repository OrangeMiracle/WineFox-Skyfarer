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
    boolean waterEscape(){return swimmer!=null;}
    boolean tick(EntityMaid maid,Vec3 target) {
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
            launch=SurfaceLaunchPlanner.choose(jump,yaw,maid.getY()+7,gravity,0,new CorridorProbe(maid,maid.getBbWidth(),.6,.4),SurfaceLaunchPlanner.DRY);
            if(launch==null)return false;
            heading.steerAngles(maid,launch.yaw(),launch.pitch(),180,90);maid.getJumpControl().jump();return true;
        }
        if(!maid.isFallFlying()&&!ElytraFlightController.startGliding(maid))return false;
        maid.setPose(Pose.FALL_FLYING);
        var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
        var space=new CorridorProbe(maid);
        if(launch!=null && departure==0) {
            launch=SurfaceLaunchPlanner.choose(start,yaw,launch.height(),gravity,0,space,SurfaceLaunchPlanner.DRY);
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
        var request=new GlidePlanner.Request(yaw,target.y+10,0,0,14,Double.NaN,remaining(),5,3,8,gravity,.25,.25,false,null);
        // Only local receding-horizon prediction, no global pathfinding along the entire journey.
        if(cached==null || ticks%5==0)cached=GlidePlanner.choose(start,request,space);
        var next=FlightPrediction.step(start,cached.yaw(),cached.pitch(),cached.yawRate(),cached.pitchRate(),cached.pitchRate(),gravity,remaining()>0);
        if(!space.clear(start,next,.04,.04))cached=GlidePlanner.choose(start,request,space);
        if(remaining()==0 && (v.horizontalDistance()<1 || !cached.safe())) {
            if(Double.isFinite(GlidePlanner.pulseCost(start,request,space,0,.75,.02))) {
                boost(maid);request=request.withPulse(remaining());cached=GlidePlanner.choose(start,request,space);
            }
        }
        lastRequest=request;if(!cached.safe())return false;
        heading.steerAngles(maid,cached.yaw(),cached.pitch(),cached.yawRate(),cached.pitchRate());return true;
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
        if(launch==null)launch=remaining()>0?SurfaceLaunchPlanner.chooseRemaining(start,yaw,height,gravity,remaining(),space,s->wet(maid,s)):
            SurfaceLaunchPlanner.choose(start,yaw,height,gravity,0,space,s->wet(maid,s));
        if(launch==null)return false;
        double pitch=SurfaceLaunchPlanner.command(start,launch,departure++,gravity,true);
        if(!space.clear(start,SurfaceLaunchPlanner.step(start,launch.yaw(),pitch,gravity,true,true),.04,.01))return false;
        if(remaining()==0){if(!Double.isFinite(SurfaceLaunchPlanner.pulseCost(start,launch,gravity,0,space,s->wet(maid,s))))return false;boost(maid);}
        heading.steerAngles(maid,launch.yaw(),pitch,180,90);return true;
    }
    void debug(net.minecraft.nbt.CompoundTag data,EntityMaid maid) {
        var v=maid.getDeltaMovement();var start=new FlightPrediction.State(maid.getX(),maid.getEyeY(),maid.getZ(),v.x,v.y,v.z,maid.getYRot(),maid.getXRot());
        var space=new CorridorProbe(maid,maid.getBbWidth(),.6,.4,waterEscape());double gravity=maid.getAttributeValue(Attributes.GRAVITY);
        if(launch!=null)dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,SurfaceLaunchPlanner.trace(start,launch,departure,gravity,remaining(),space,s->wet(maid,s)));
        else if(cached!=null&&lastRequest!=null)dev.winefox.skyfarer.runtime.DebugSnapshot.corridor(data,maid,GlidePlanner.trace(start,lastRequest.withPulse(remaining()),cached,space));
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
    void close(){if(rocket!=null)rocket.discard();rocket=null;restoreSwimming();}
}
