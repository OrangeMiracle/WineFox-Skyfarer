package dev.winefox.skyfarer.runtime;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.winefox.skyfarer.client.CometGlowParticle;
import dev.winefox.skyfarer.flight.*;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Execute the REAL particle constructor/tick/render with a UV-only sprite and a vertex sink.
 * No ClientLevel, game, graphics context, launcher or server is created. */
public final class CometRadianceChecks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        geometry();gateAndBudget();hooks();
        System.out.println("Actual fullbright particle vertices, two-sided ring plane/expansion, fade/lifetime, burst re-arming, bounded multi-maid geometry and native drop hooks passed");
    }
    static SpriteSet uvSprite()throws Exception {
        // Only the atlas UV rectangle is substituted: actual MC sprite accessors and
        // the production particle renderer run unchanged, without loading OpenGL.
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)field.get(null);
        var sprite=(TextureAtlasSprite)unsafe.allocateInstance(TextureAtlasSprite.class);
        for(String name:List.of("u1","v1")){var f=TextureAtlasSprite.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(sprite,1);}
        return new SpriteSet(){public TextureAtlasSprite get(int age,int life){return sprite;}public TextureAtlasSprite get(RandomSource random){return sprite;}};
    }
    static final class Sink implements VertexConsumer {
        final List<Vec3> points=new ArrayList<>();int lightCount;int maxAlpha;
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"nonfinite vertex");points.add(new Vec3(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){maxAlpha=Math.max(maxAlpha,a);return this;}
        public VertexConsumer setUv(float u,float v){check(u>=0&&u<=1&&v>=0&&v<=1,"UV outside atlas sprite");return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){check((u|(v<<16))==LightTexture.FULL_BRIGHT,"vertex depends on world light");lightCount++;return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        double radius(Vec3 origin){return points.stream().mapToDouble(p->p.distanceTo(origin)).max().orElseThrow();}
    }
    static void geometry()throws Exception {
        var sprites=uvSprite();var camera=new Camera();
        for(var axis:List.of(new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,2,3).normalize()))for(int side:new int[]{-1,1}){
            var origin=axis.scale(side*5);
            var ring=new CometGlowParticle(null,origin.x,origin.y,origin.z,0,0,0,sprites,true);ring.configure(.5f,axis);
            var first=new Sink();ring.render(first,camera,0);
            check(first.points.size()==ThrustVisuals.RING_POINTS*8&&first.lightCount==first.points.size(),"ring vertices/light count");
            for(var p:first.points)check(Math.abs(p.subtract(origin).dot(axis))<1e-5,"ring not perpendicular to thrust");
            var normal=first.points.get(1).subtract(first.points.get(0)).cross(first.points.get(2).subtract(first.points.get(0)));
            check(normal.dot(origin.scale(-1))>0,"ring culled from camera side "+side);
            double small=first.radius(origin);
            for(int tick=0;tick<8;tick++)ring.tick();
            var later=new Sink();ring.render(later,camera,0);
            check(later.radius(origin)>small*2&&later.maxAlpha<first.maxAlpha,"ring did not expand and fade");
            for(int tick=0;tick<6;tick++)ring.tick();check(!ring.isAlive(),"ring never expired");
        }
        for(float power:new float[]{0,.125f,1}){
            var tail=new CometGlowParticle(null,0,0,5,0,0,-.03,sprites,false);tail.configure(power,new Vec3(0,0,1));
            var vertices=new Sink();tail.render(vertices,camera,0);
            check(vertices.points.size()==8&&vertices.lightCount==8&&tail.getLightColor(.5f)==0xF000F0,"dual-layer tail not fullbright without a world");
            check(tail.getLifetime()>=14&&tail.getLifetime()<=20,"unbounded tail life");
            for(int tick=0;tick<22;tick++)tail.tick();check(!tail.isAlive(),"tail never expired");
        }
        check(ThrustVisuals.samples(1.25*1.25,6)==5&&ThrustVisuals.samples(100,6)==1,"cruise gap/teleport bridge");
        System.out.println("Rendered real particles into vertex sink: every light UV is 0xF000F0, ring visible from both sides on 3 axes, 12-tick ring and 14-20-tick tails expire");
    }
    static void gateAndBudget(){
        var gate=new ThrustEffectGate();var events=new ArrayList<Integer>();
        for(int t=0;t<100;t++){
            float power=t<10?.18f:t<40?.025f:t<60?.16f:t<66?0:.10f;
            var frame=gate.actual(t,power);if(frame!=null&&frame.ring())events.add(t);
            check(gate.actual(t,power)==null&&gate.finish(t)==null,"same-tick duplicate");
        }
        check(events.equals(List.of(0,40,66)),"launch peak prevented later reacceleration: "+events);
        var stop=gate.finish(100);check(stop!=null&&stop.power()==0,"missing stop frame");
        var budget=new ThrustVisuals.Budget();
        for(int t=0;t<200;t++){
            int tails=0,rings=0;
            for(int maid=0;maid<128;maid++){
                for(int j=0;j<6;j++)if(budget.take(t,2,false))tails+=2;
                if(budget.take(t,ThrustVisuals.RING_POINTS,true))rings++;
            }
            check(tails==96&&rings==2,"crowded-screen work not bounded");
        }
        System.out.println("128-emitter / 200-tick budget: <=96 tail particles + 2 ring particles per tick; reacceleration events at [0, 40, 66]");
    }
    static void hooks()throws Exception {
        var resources=CometRadianceChecks.class.getClassLoader();
        for(var id:List.of("comet_tail","comet_ring")){
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/winefox_skyfarer/particles/"+id+".json"))).getAsJsonObject();
            for(var sprite:json.getAsJsonArray("textures")){
                var parts=sprite.getAsString().split(":");try(var png=resources.getResourceAsStream("assets/"+parts[0]+"/textures/particle/"+parts[1]+".png")){
                    check(png!=null&&javax.imageio.ImageIO.read(png)!=null,"missing actual atlas sprite");
                }
            }
        }
        check(CometDataChecks.calls("dev/winefox/skyfarer/client/SkyfarerClient.class","particles").stream().filter(s->s.contains("registerSpriteSet")).count()==2,"providers not registered");
        var itemHook=CometDataChecks.calls("dev/winefox/skyfarer/mixin/CometRitualItemMixin.class",null);
        check(itemHook.stream().anyMatch(s->s.contains("CometRelicDrops.protectedFrom"))&&itemHook.stream().anyMatch(s->s.contains("CometRelicDrops.refresh")),"item damage/tick hooks disconnected");
        check(CometDataChecks.calls("net/minecraft/world/entity/Entity.class","thunderHit").stream().anyMatch(s->s.contains("Entity.hurt(")),"lightning bypasses item damage hook");
        check(CometDataChecks.calls("net/minecraft/client/renderer/LevelRenderer.class",null).stream().anyMatch(s->s.contains("Entity.getTeamColor(")),"native outline color API changed");
        var mixins=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/winefox_skyfarer.mixins.json"))).getAsJsonObject();
        check(mixins.getAsJsonArray("client").toString().contains("CometOutlineMixin")&&!mixins.getAsJsonArray("mixins").toString().contains("CometOutlineMixin"),"outline hook leaks to server");
        String fire;try(var input=resources.getResourceAsStream("data/minecraft/tags/damage_type/is_fire.json")){fire=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        check(fire.contains("minecraft:lava")&&fire.contains("minecraft:in_fire")&&fire.contains("minecraft:on_fire"),"native fire tag no longer covers lava/fire");
    }
}
