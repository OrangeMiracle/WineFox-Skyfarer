package dev.winefox.skyfarer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.winefox.skyfarer.flight.ThrustVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;
import java.util.Optional;

/** Atlas-backed fullbright quads in vanilla's translucent particle pass. No custom shader. */
public final class CometGlowParticle extends TextureSheetParticle {
    private static final ParticleGroup GROUP=new ParticleGroup(2304);
    private final boolean ring;
    private ThrustVisuals.Frame plane=ThrustVisuals.frame(0,0,1,0,0);

    public CometGlowParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,boolean ring){
        super(level,x,y,z);
        this.ring=ring;pickSprite(sprites);
        hasPhysics=false;gravity=0;friction=ring?1:.92f;
        setParticleSpeed(vx,vy,vz);configure(0,Vec3.ZERO);
    }
    public void configure(float power,Vec3 direction){
        power=Math.clamp(power,0,1);
        lifetime=ring?12:14+Math.round(power*6);
        quadSize=.20f+.14f*power;
        if(ring)plane=ThrustVisuals.frame(direction.x,direction.y,direction.z,0,0);
    }
    @Override public int getLightColor(float partialTick){return LightTexture.FULL_BRIGHT;}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override public Optional<ParticleGroup> getParticleGroup(){return Optional.of(GROUP);}
    @Override public AABB getRenderBoundingBox(float partialTick){return getBoundingBox().inflate(ring?2.4:.5);}
    @Override public void render(VertexConsumer vertices,Camera camera,float partialTick){
        float progress=Math.clamp((age+partialTick)/lifetime,0,1);
        float fade=ThrustVisuals.fade(progress);
        if(ring){
            Vec3 origin=new Vec3(Mth.lerp(partialTick,xo,x),Mth.lerp(partialTick,yo,y),Mth.lerp(partialTick,zo,z)).subtract(camera.getPosition());
            double radius=ThrustVisuals.ringRadius(progress);
            // Two soft strips share one particle and a fixed plane, so the circle cannot
            // collapse into a billboard when viewed from the side or detach on a turn.
            strip(vertices,origin,radius,.14*(1-progress)+.035,fade*.45f);
            strip(vertices,origin,radius,.055*(1-progress)+.015,fade);
        }else{
            float size=quadSize;
            alpha=fade*.85f;setColor(1,.035f,.07f);super.render(vertices,camera,partialTick);
            quadSize=size*.43f;alpha=fade;setColor(1,.40f,.32f);super.render(vertices,camera,partialTick);
            quadSize=size;
        }
    }
    private void strip(VertexConsumer vertices,Vec3 origin,double radius,double width,float opacity){
        // Sample the sprite's central columns along the arc: soft radial edges,
        // continuous brightness at segment joins instead of a dotted necklace.
        float u0=Mth.lerp(.4f,getU0(),getU1()),u1=Mth.lerp(.6f,getU0(),getU1());
        for(int i=0;i<ThrustVisuals.RING_POINTS;i++){
            var a=plane.radial(2*Math.PI*i/ThrustVisuals.RING_POINTS);
            var b=plane.radial(2*Math.PI*(i+1)/ThrustVisuals.RING_POINTS);
            // Vanilla's particle pass can inherit face culling. Wind toward the camera
            // without changing the physical plane or adding another set of quads.
            var forward=plane.forward();
            if(origin.x*forward.x()+origin.y*forward.y()+origin.z*forward.z()<0){var swap=a;a=b;b=swap;}
            vertex(vertices,origin,a,radius-width,u0,getV0(),opacity);
            vertex(vertices,origin,a,radius+width,u0,getV1(),opacity);
            vertex(vertices,origin,b,radius+width,u1,getV1(),opacity);
            vertex(vertices,origin,b,radius-width,u1,getV0(),opacity);
        }
    }
    private void vertex(VertexConsumer vertices,Vec3 origin,ThrustVisuals.Vector radial,double radius,float u,float v,float opacity){
        vertices.addVertex((float)(origin.x+radial.x()*radius),(float)(origin.y+radial.y()*radius),(float)(origin.z+radial.z()*radius))
            .setUv(u,v).setColor(1f,1f,1f,opacity).setLight(LightTexture.FULL_BRIGHT);
    }
}
