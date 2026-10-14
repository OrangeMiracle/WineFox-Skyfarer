package dev.winefox.skyfarer.client;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.flight.ThrustVisuals;
import dev.winefox.skyfarer.network.*;
import net.minecraft.client.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Receives actual output only. Geometry, interpolation and all particle work stay client-side. */
@EventBusSubscriber(modid="winefox_skyfarer",value=Dist.CLIENT)
public final class CometThrustParticles {
    private static final class State {
        CometThrustPayload packet;
        long until,ringUntil=Long.MIN_VALUE,lastRing=Long.MIN_VALUE,lastTick=Long.MIN_VALUE;
        Vec3 left,right;
    }
    private static final Map<Integer,State> active=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final ThrustVisuals.Budget budget=new ThrustVisuals.Budget();
    private static long received,spawned,missing,culled,limited,providerMissing,lastReport;

    public static void accept(CometThrustPayload p){
        var mc=Minecraft.getInstance();resetWorld(mc);if(world==null)return;
        received++;
        var known=world.getEntity(p.entity());
        if(known!=null&&!known.getUUID().equals(p.identity()))return;
        var existing=active.get(p.entity());
        // ID reuse must not let a late stop clear a different maid's effect.
        if(p.power()==0){if(existing!=null&&existing.packet.identity().equals(p.identity()))active.remove(p.entity());return;}
        if(active.size()>=ThrustVisuals.MAX_TRACKED&&existing==null){limited++;return;}
        if(existing==null||!existing.packet.identity().equals(p.identity())){existing=new State();active.put(p.entity(),existing);}
        long now=world.getGameTime();existing.packet=p;existing.until=now+ThrustVisuals.PACKET_TTL;
        if(p.ring()&&(existing.lastRing==Long.MIN_VALUE||now-existing.lastRing>=20)){
            existing.ringUntil=now+ThrustVisuals.RING_TTL;existing.lastRing=now;
        }
        // Entity spawn/tracking packets may arrive later. Keep the bounded state until
        // TTL instead of dropping the first acceleration event at receipt.
    }
    private static void resetWorld(Minecraft mc){
        if(world!=mc.level){active.clear();world=mc.level;received=spawned=missing=culled=limited=providerMissing=0;lastReport=0;}
    }
    private static boolean visible(Minecraft mc,Entity entity){
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position())<96*96;
    }
    private static Vec3[] frame(Entity entity,boolean thrust){
        var v=thrust?Vec3.ZERO:entity.getDeltaMovement();var f=ThrustVisuals.frame(v.x,v.y,v.z,entity.getYRot(),entity.getXRot());
        return new Vec3[]{vector(f.forward()),vector(f.right()),vector(f.up())};
    }
    private static Vec3 vector(ThrustVisuals.Vector v){return new Vec3(v.x(),v.y(),v.z());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();resetWorld(mc);if(world==null||mc.isPaused())return;
        long now=world.getGameTime();
        active.entrySet().removeIf(entry->now>=entry.getValue().until);
        // Rotate a stable order so a crowded screen cannot permanently starve later maids.
        var ids=new ArrayList<>(active.keySet());Collections.sort(ids);
        for(int index=0;index<ids.size();index++){
            int id=ids.get((index+(int)Math.floorMod(now,ids.size()))%ids.size());
            var state=active.get(id);var entity=world.getEntity(id);
            if(entity==null){missing++;continue;}
            if(!entity.getUUID().equals(state.packet.identity())||!entity.isAlive()){active.remove(id);continue;}
            if(state.lastTick==now)continue;state.lastTick=now;
            if(!visible(mc,entity)){culled++;state.left=state.right=null;continue;}
            var quality=mc.options.particles().get();
            var basis=frame(entity,false);float force=state.packet.power()/255f;
            var center=entity.position().add(0,entity.getBbHeight()*.6,0).add(basis[0].scale(-.55));
            if(now<state.ringUntil&&budget.take(now,ThrustVisuals.RING_POINTS,true)){
                var thrust=frame(entity,true);
                particle(CometParticles.RING.get(),center.add(thrust[0].scale(-.3)),thrust[0].scale(-.03),force,thrust[0]);
                state.ringUntil=Long.MIN_VALUE;
            }
            var left=center.add(basis[1].scale(-.38));var right=center.add(basis[1].scale(.38));
            if(quality==ParticleStatus.MINIMAL&&(now+id)%2!=0){state.left=state.right=null;continue;}
            int limit=quality==ParticleStatus.ALL?6:quality==ParticleStatus.DECREASED?3:1;
            double distance=state.left==null?0:Math.max(state.left.distanceToSqr(left),state.right.distanceToSqr(right));
            int samples=ThrustVisuals.samples(distance,limit);
            var velocity=basis[0].scale(-.025-force*.035);
            for(int i=1;i<=samples;i++){
                // Reserve pairs together; budget exhaustion never leaves one engine lit.
                if(!budget.take(now,2,false)){limited++;break;}
                double t=(double)i/samples;
                particle(CometParticles.TAIL.get(),samples==1?left:state.left.lerp(left,t),velocity,force,basis[0]);
                particle(CometParticles.TAIL.get(),samples==1?right:state.right.lerp(right,t),velocity,force,basis[0]);
            }
            state.left=left;state.right=right;
        }
        if(now-lastReport>=100&&dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging()){
            lastReport=now;WineFoxSkyfarer.LOGGER.info("[WFS comet-fx client] rx={} active={} spawned={} awaitingEntity={} distanceCulled={} budgetLimited={} providerMissing={}",received,active.size(),spawned,missing,culled,limited,providerMissing);
        }
    }
    private static void particle(SimpleParticleType type,Vec3 point,Vec3 velocity,float force,Vec3 direction){
        var particle=Minecraft.getInstance().particleEngine.createParticle(type,point.x,point.y,point.z,velocity.x,velocity.y,velocity.z);
        if(particle instanceof CometGlowParticle glow){glow.configure(force,direction);spawned++;}else providerMissing++;
    }
}
