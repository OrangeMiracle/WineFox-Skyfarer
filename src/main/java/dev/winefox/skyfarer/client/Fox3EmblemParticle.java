package dev.winefox.skyfarer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.winefox.skyfarer.weapon.Fox3BurstVisuals;
import dev.winefox.skyfarer.weapon.Fox3EmblemData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import java.util.Optional;

/** One engine particle batches independent spark quads, never a face-textured billboard.
 * Each viewer's camera supplies the whole constellation's plane, including in third person. */
public final class Fox3EmblemParticle extends TextureSheetParticle {
    private static final ParticleGroup GROUP = new ParticleGroup(Fox3BurstVisuals.MAX_ACTIVE);
    public Fox3EmblemParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites) {
        super(level,x,y,z);
        pickSprite(sprites);
        lifetime=Fox3BurstVisuals.LIFE;
        hasPhysics=false;
    }
    @Override public void tick() {
        if (age==0) {
            boolean far=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x,y,z)>=256;
            level.playLocalSound(x,y,z,far?SoundEvents.FIREWORK_ROCKET_BLAST_FAR:SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.AMBIENT,20,.95f+random.nextFloat()*.1f,true);
        }
        if (++age>=lifetime) remove();
    }
    @Override public Optional<ParticleGroup> getParticleGroup() { return Optional.of(GROUP); }
    @Override public int getLightColor(float partial) { return LightTexture.FULL_BRIGHT; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public AABB getRenderBoundingBox(float partial) { return getBoundingBox().inflate(8); }
    @Override public void render(VertexConsumer buffer,Camera camera,float partial) {
        float time=age+partial;
        var origin=camera.getPosition();
        var right=new Vector3f(1,0,0).rotate(camera.rotation());
        var up=new Vector3f(0,1,0).rotate(camera.rotation());
        float ox=(float)(x-origin.x), oy=(float)(y-origin.y)+Fox3BurstVisuals.lift(time), oz=(float)(z-origin.z);
        float radius=Fox3BurstVisuals.radius(time), fade=Fox3BurstVisuals.fade(time);
        for (int i=0;i<Fox3EmblemData.COUNT;i++) {
            float px=Fox3EmblemData.x(i), py=Fox3EmblemData.y(i);
            float drift=Fox3BurstVisuals.scatter(time);
            float dx=px*radius+(float)Math.sin(i*2.4)*.10f*drift;
            float dy=py*radius-drift*.15f*(i%5);
            float opacity=fade*Fox3BurstVisuals.sparkle(i,time)*Math.clamp(time/2,0,1);
            spark(buffer,right,up,ox,oy,oz,dx,dy,.07f,opacity,i);
            // A short, sparse expansion trail uses one extra quad, never child particles.
            if (i%4==0 && time<10) {
                float trailRadius=Fox3BurstVisuals.radius(Math.max(0,time-1.3f));
                spark(buffer,right,up,ox,oy,oz,px*trailRadius,py*trailRadius,.04f,opacity*.3f,i);
            }
        }
    }
    private void spark(VertexConsumer b,Vector3f r,Vector3f u,float ox,float oy,float oz,
                       float px,float py,float size,float opacity,int index) {
        float green=.52f+.22f*(index%5)/4;
        vertex(b,r,u,ox,oy,oz,px+size,py-size,getU1(),getV1(),green,opacity);
        vertex(b,r,u,ox,oy,oz,px+size,py+size,getU1(),getV0(),green,opacity);
        vertex(b,r,u,ox,oy,oz,px-size,py+size,getU0(),getV0(),green,opacity);
        vertex(b,r,u,ox,oy,oz,px-size,py-size,getU0(),getV1(),green,opacity);
    }
    private static void vertex(VertexConsumer b,Vector3f r,Vector3f up,float ox,float oy,float oz,
                               float x,float y,float u,float v,float green,float opacity) {
        b.addVertex(ox+r.x*x+up.x*y,oy+r.y*x+up.y*y,oz+r.z*x+up.z*y)
            .setUv(u,v).setColor(1f,green,.12f,opacity).setLight(LightTexture.FULL_BRIGHT);
    }
}
