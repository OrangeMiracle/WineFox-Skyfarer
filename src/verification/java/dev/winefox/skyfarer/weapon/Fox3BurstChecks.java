package dev.winefox.skyfarer.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.winefox.skyfarer.client.Fox3EmblemParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Executes the actual batched renderer into a vertex recorder; no window, world or GL context. */
public final class Fox3BurstChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var png=Path.of("tools/素材参考/爆炸特效.png");
        require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(png))).equals(Fox3EmblemData.SOURCE_SHA256),"sampling source matches supplied PNG");
        var image=ImageIO.read(png.toFile());
        require(Fox3EmblemData.COUNT==444 && Fox3EmblemData.COUNT<=512,"bounded sample count");
        for(int i=0;i<Fox3EmblemData.COUNT;i++) {
            float x=Fox3EmblemData.x(i), y=Fox3EmblemData.y(i);
            require(Math.abs(x)<=1 && Math.abs(y)<=1,"normalized point");
            int px=Math.round((x+1)*.5f*(image.getWidth()-1)), py=Math.round((1-y)*.5f*(image.getHeight()-1));
            boolean ink=false;
            for(int dy=-5;dy<=5;dy++) for(int dx=-5;dx<=5;dx++) {
                int rgb=image.getRGB(Math.clamp(px+dx,0,image.getWidth()-1),Math.clamp(py+dy,0,image.getHeight()-1));
                if (((rgb>>16)&255)<120 && ((rgb>>8)&255)<120 && (rgb&255)<120) ink=true;
            }
            require(ink,"point remains on source black ink");
            for(int j=0;j<i;j++) require(Math.hypot(x-Fox3EmblemData.x(j),y-Fox3EmblemData.y(j))>.037,"no dense duplicate samples");
        }
        require(Fox3BurstVisuals.event(true)==18 && Fox3BurstVisuals.event(false)==17,"one fair boolean maps to exactly two exclusive events");
        var random=RandomSource.create(77); int fox=0;
        for(int i=0;i<10000;i++) if(Fox3BurstVisuals.event(random.nextBoolean())==18) fox++;
        require(fox>4800 && fox<5200,"seeded fair selection sanity");
        require(Fox3BurstVisuals.expansion(0)==0 && Fox3BurstVisuals.expansion(8)==1,"rapid expansion endpoints");
        require(Fox3BurstVisuals.fade(22)==1 && Fox3BurstVisuals.fade(40)==0,"hold then fade endpoints");
        for(int i=0;i<=80;i++) require(Fox3BurstVisuals.fade(i/2f)>=0 && Fox3BurstVisuals.fade(i/2f)<=1,"bounded fade");
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);
        var sprite=(TextureAtlasSprite)unsafe.allocateInstance(TextureAtlasSprite.class);
        for(String name:List.of("u1","v1")) { var f=TextureAtlasSprite.class.getDeclaredField(name); f.setAccessible(true); f.setFloat(sprite,1); }
        SpriteSet sprites=new SpriteSet() {
            public TextureAtlasSprite get(int age,int life) { return sprite; }
            public TextureAtlasSprite get(RandomSource r) { return sprite; }
        };
        var particle=new Fox3EmblemParticle(null,0,0,0,sprites);
        require(particle.getParticleGroup().orElseThrow().getLimit()==16,"engine active group is bounded");
        require(particle.getLightColor(0)==0xF000F0,"fullbright night lighting");
        var engine=(ParticleEngine)unsafe.allocateInstance(ParticleEngine.class);
        var queue=new ArrayDeque<Particle>();
        var counts=new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<net.minecraft.core.particles.ParticleGroup>();
        set(engine,"particlesToAdd",queue); set(engine,"trackedParticleCounts",counts);
        set(engine,"particles",new HashMap<>()); set(engine,"trackingEmitters",new ArrayDeque<>());
        for(int i=0;i<32;i++) engine.add(new Fox3EmblemParticle(null,0,0,0,sprites));
        require(queue.size()==16,"actual engine caps four simultaneous eight-missile salvos at 16 whole emblems");
        engine.setLevel(null);
        require(queue.isEmpty() && counts.isEmpty(),"actual world unload clears queued effects and group counts");
        engine.add(new Fox3EmblemParticle(null,0,0,0,sprites)); require(queue.size()==1,"new world can spawn after cleanup");
        var age=Particle.class.getDeclaredField("age"); age.setAccessible(true);
        // Six very different cameras must see identical unmirrored local facial points.
        for(float yaw:new float[]{0,90,180,270}) for(float pitch:new float[]{-65,0,65}) {
            var camera=new Camera(); camera.rotation().rotationYXZ((float)Math.toRadians(yaw),(float)Math.toRadians(pitch),0);
            age.setInt(particle,16); var recorder=new Recorder(); particle.render(recorder,camera,0);
            require(recorder.vertices.size()==444*4,"hold draws exactly one quad per sampled point");
            for(int i=0;i<444;i++) {
                var center=new Vector3f(); for(int j=0;j<4;j++) center.add(recorder.vertices.get(i*4+j));
                center.mul(.25f).sub(0,Fox3BurstVisuals.lift(16),0).rotate(camera.rotation().conjugate(new org.joml.Quaternionf()));
                require(Math.abs(center.x-Fox3EmblemData.x(i)*3)<1e-5 && Math.abs(center.y-Fox3EmblemData.y(i)*3)<1e-5 && Math.abs(center.z)<1e-5,"camera-facing geometry preserves face coordinates");
            }
        }
        var camera=new Camera();
        var frames=new com.google.gson.JsonArray();
        for(int t:new int[]{0,4,8,22,32,40}) {
            age.setInt(particle,t); var r=new Recorder(); particle.render(r,camera,0);
            require(r.vertices.size()<=555*4,"bounded sparse trails; max 8880 quads for 16 effects");
            for(var v:r.vertices) require(particle.getRenderBoundingBox(0).contains(v.x,v.y,v.z),"all sparks stay inside culling bounds");
            if(t==40) require(r.maxAlpha==0,"fully dissipated at deadline");
            var frame=new com.google.gson.JsonObject(); frame.addProperty("tick",t); var dots=new com.google.gson.JsonArray();
            for(int i=0;i<r.vertices.size();i+=4) {
                var center=new Vector3f(); for(int j=0;j<4;j++) center.add(r.vertices.get(i+j)); center.mul(.25f);
                var dot=new com.google.gson.JsonArray(); dot.add(center.x); dot.add(center.y);
                dot.add(r.vertices.get(i).distance(r.vertices.get(i+1)));
                for(int color:r.colors.get(i)) dot.add(color); dots.add(dot);
            }
            frame.add("sparks",dots); frames.add(frame);
        }
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/fox3-burst-vertices.json"),frames.toString());
        age.setInt(particle,39); particle.tick(); require(!particle.isAlive(),"actual particle removes at 40 ticks");
        var resource=Files.readString(Path.of("src/main/resources/assets/winefox_skyfarer/particles/fox3_emblem.json"));
        require(resource.contains("minecraft:spark_5") && !resource.contains("爆炸特效"),"only vanilla spark sprite, never face PNG");
        System.out.println("Fox3 burst: source sampling, fair selection, actual vertex rendering from 12 cameras, expansion/hold/fade, fullbright, bounds and expiry passed ("+checks+" assertions).");
    }
    private static class Recorder implements VertexConsumer {
        final List<Vector3f> vertices=new ArrayList<>(); final List<int[]> colors=new ArrayList<>(); int maxAlpha;
        public VertexConsumer addVertex(float x,float y,float z) { require(Float.isFinite(x+y+z),"finite vertices"); vertices.add(new Vector3f(x,y,z)); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { require(r==255 && g>=132 && g<=189 && b==30 && a>=0 && a<=255,"orange gold and bounded alpha"); colors.add(new int[]{r,g,b,a}); maxAlpha=Math.max(maxAlpha,a); return this; }
        public VertexConsumer setUv(float u,float v) { require(u>=0 && u<=1 && v>=0 && v<=1,"valid sprite UV"); return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { require(u==240 && v==240,"emitted vertices fullbright"); return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
    private static void set(Object target,String field,Object value) throws Exception {
        var f=target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target,value);
    }
    private static void require(boolean ok,String reason) { checks++; if(!ok) throw new AssertionError(reason); }
}
