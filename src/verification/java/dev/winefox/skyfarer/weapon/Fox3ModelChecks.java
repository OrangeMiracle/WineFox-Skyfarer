package dev.winefox.skyfarer.weapon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3d;
import org.joml.Matrix4d;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real model resources and production orientation math; no client or server is started. */
public final class Fox3ModelChecks {
    private static int checks;
    private static final Path ASSETS = Path.of("src/main/resources/assets/winefox_skyfarer");
    public static void main(String[] args) throws Exception {
        resources();
        for (var v : new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),
                new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,2,3),new Vec3(-3,-4,-5)}) aligned(v);
        var still = Fox3VisualOrientation.fromVelocity(new Vec3(1e-8,-1e-8,0),137,-42);
        require(still.yaw()==137 && still.pitch()==-42,"near-zero velocity retains last valid orientation");
        require(Fox3VisualOrientation.fromVelocity(new Vec3(0,2,0),137,0).yaw()==137,"vertical motion retains yaw");
        float seam = Mth.rotLerp(.5f,179,-179);
        require(Math.abs(seam-180)<1e-5,"yaw interpolation uses short arc across seam");
        var seamForward = Fox3VisualOrientation.rotation(seam,0).transform(new Vector3f(0,0,1));
        require(seamForward.z < -.999,"interpolated seam does not point backward");
        // Feed the renderer's real mapping with the production climb/rollover/tracking velocities.
        for(int side : new int[]{-1,1}) {
            var launch = Fox3Guidance.launchVelocity(0,1,side);
            var velocity = launch;
            var position = new Fox3Guidance.Vector(0,20,0);
            var target = new Fox3Guidance.Vector(25,0,35);
            var phase = Fox3Guidance.Phase.CLIMB;
            for(int tick=1;tick<=Fox3Guidance.MAX_LIFE_TICKS;tick++) {
                var aim = target.add(position.scale(-1));
                phase = Fox3Guidance.nextPhase(phase,tick,velocity,aim);
                velocity = Fox3Guidance.step(velocity,phase==Fox3Guidance.Phase.CLIMB
                    ? Fox3Guidance.climbDirection(launch,tick) : aim,phase);
                aligned(new Vec3(velocity.x(),velocity.y(),velocity.z()));
                position=position.add(velocity);
            }
        }
        System.out.println("Fox3 tail model: " + checks + " assertions passed: packaged mesh/PNG, outward normals/UVs, six axes, climb/rollover/dive, zero speed, yaw seam and rear exhaust.");
    }
    private static void aligned(Vec3 velocity) {
        var a=Fox3VisualOrientation.fromVelocity(velocity,23,-17);
        var actual=Fox3VisualOrientation.rotation(a.yaw(),a.pitch()).transform(new Vector3f(0,0,1));
        var expected=velocity.normalize();
        require(new Vec3(actual.x,actual.y,actual.z).distanceTo(expected)<1e-5,"white tip follows actual velocity: "+velocity);
        var nozzle=Fox3VisualOrientation.nozzle(a.yaw(),a.pitch());
        require(nozzle.normalize().dot(expected)<-.99999,"exhaust is behind the projectile");
        require(Math.abs(nozzle.length()+Fox3VisualOrientation.NOZZLE_Z)<1e-6,"exhaust distance is constant under rotation");
    }
    private static void resources() throws Exception {
        var model=JsonParser.parseString(Files.readString(ASSETS.resolve("models/entity/fox_tail_missile.json"))).getAsJsonObject();
        require(model.get("front_axis").getAsString().equals("+Z"),"canonical model forward");
        var tip=vec(model.getAsJsonArray("tip")); var nozzle=vec(model.getAsJsonArray("nozzle"));
        require(tip.z>0 && Math.hypot(tip.x,tip.y)<1e-7,"white Tail7 is on forward axis");
        require(Math.abs(nozzle.z-Fox3VisualOrientation.NOZZLE_Z)<1e-7 && Math.hypot(nozzle.x,nozzle.y)<1e-7,"renderer exhaust matches baked nozzle");
        var source=Path.of("tools/素材参考/尾巴导弹");
        var digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source.resolve("fox_tail_missile.bbmodel"))));
        require(model.get("source_sha256").getAsString().equals(digest),"bake matches artist source");
        var texture=ASSETS.resolve("textures/entity/fox_tail_missile.png");
        require(Arrays.equals(Files.readAllBytes(texture),Files.readAllBytes(source.resolve("fox_tail_missile.png"))),"artist PNG copied byte-for-byte");
        var png=ImageIO.read(texture.toFile());
        require(png!=null && png.getWidth()==128 && png.getHeight()==128,"PNG decodes at UV dimensions");
        var faces=model.getAsJsonArray("faces");
        require(model.get("cubes").getAsInt()==27 && faces.size()==162,"all artist cubes/faces retained");
        sourceTransforms(JsonParser.parseString(Files.readString(source.resolve("fox_tail_missile.bbmodel"))).getAsJsonObject(),faces);
        for(int cube=0;cube<27;cube++) {
            Vec3 center=Vec3.ZERO;
            for(int f=0;f<6;f++) for(var p:faces.get(cube*6+f).getAsJsonObject().getAsJsonArray("vertices")) center=center.add(vec(p.getAsJsonArray()).scale(1.0/24));
            for(int f=0;f<6;f++) {
                var face=faces.get(cube*6+f).getAsJsonObject(); var points=face.getAsJsonArray("vertices"); var n=vec(face.getAsJsonArray("normal"));
                require(Math.abs(n.length()-1)<1e-6,"unit lighting normal");
                var cross=vec(points.get(1).getAsJsonArray()).subtract(vec(points.get(0).getAsJsonArray()))
                    .cross(vec(points.get(2).getAsJsonArray()).subtract(vec(points.get(0).getAsJsonArray())));
                // Vec3.normalize intentionally zeroes tiny gameplay vectors; these thin art faces
                // have valid cross products below that gameplay threshold.
                require(cross.length()>1e-8 && cross.dot(n)/cross.length()>.999,"CCW winding agrees with lighting");
                Vec3 faceCenter=Vec3.ZERO;
                for(var p:points) {
                    var v=p.getAsJsonArray(); faceCenter=faceCenter.add(vec(v).scale(.25));
                    for(int i=0;i<5;i++) require(Double.isFinite(v.get(i).getAsDouble()),"finite vertex/UV");
                    for(int i=3;i<5;i++) require(v.get(i).getAsDouble()>=0 && v.get(i).getAsDouble()<=1,"UV inside PNG");
                }
                require(faceCenter.subtract(center).dot(n)>0,"normal points outside cube");
            }
        }
        for(String resource:new String[]{"models/entity/fox_tail_missile.json","textures/entity/fox_tail_missile.png"})
            try(var input=Fox3ModelChecks.class.getResourceAsStream("/assets/winefox_skyfarer/"+resource)) {
                require(input!=null && Arrays.equals(input.readAllBytes(),Files.readAllBytes(ASSETS.resolve(resource))),"runtime resource path resolves: "+resource);
            }
    }
    private record SourceCube(JsonObject data,Matrix4d matrix) {
        Vector3d center() { return matrix.transformPosition(v3(data,"from").add(v3(data,"to")).mul(.5)); }
    }
    private static void sourceTransforms(JsonObject source,JsonArray faces) {
        Map<String,JsonObject> elements=new LinkedHashMap<>();
        for(var item:source.getAsJsonArray("elements")) { var e=item.getAsJsonObject(); elements.put(e.get("uuid").getAsString(),e); }
        List<SourceCube> cubes=new ArrayList<>();
        collect(source.getAsJsonArray("outliner"),new Matrix4d(),elements,cubes);
        var tip=cubes.stream().filter(c->c.data.get("name").getAsString().equals("Tail7")).findFirst().orElseThrow().center();
        var nozzle=cubes.stream().filter(c->c.data.get("name").getAsString().equals("Nozzle_Warm_Core")).findFirst().orElseThrow().center();
        var origin=new Vector3d(tip).add(nozzle).mul(.5);
        var forward=new Vector3d(tip).sub(nozzle).normalize();
        var right=new Vector3d(0,1,0).cross(forward).normalize(); var up=new Vector3d(forward).cross(right);
        for(int i=0;i<cubes.size();i++) {
            var cube=cubes.get(i); var from=v3(cube.data,"from"); var to=v3(cube.data,"to");
            List<Vec3> corners=new ArrayList<>();
            for(int k=0;k<8;k++) {
                var p=cube.matrix.transformPosition(new Vector3d((k&1)==0?from.x:to.x,(k&2)==0?from.y:to.y,(k&4)==0?from.z:to.z)).sub(origin);
                corners.add(new Vec3(p.dot(right)/32,p.dot(up)/32,p.dot(forward)/32));
            }
            for(int f=0;f<6;f++) for(var vertex:faces.get(i*6+f).getAsJsonObject().getAsJsonArray("vertices")) {
                Vec3 actual=vec(vertex.getAsJsonArray());
                require(corners.stream().anyMatch(p->p.distanceTo(actual)<1e-6),"baked vertex matches independent JOML ZYX hierarchy: cube "+i);
            }
        }
    }
    private static void collect(JsonArray children,Matrix4d parent,Map<String,JsonObject> elements,List<SourceCube> cubes) {
        for(var child:children) {
            var data=child.isJsonPrimitive()?elements.get(child.getAsString()):child.getAsJsonObject();
            var o=v3(data,"origin"); var r=v3(data,"rotation").mul(Math.PI/180);
            var matrix=new Matrix4d(parent).translate(o).rotateZYX(r.z,r.y,r.x).translate(-o.x,-o.y,-o.z);
            if(child.isJsonPrimitive()) cubes.add(new SourceCube(data,matrix));
            else collect(data.getAsJsonArray("children"),matrix,elements,cubes);
        }
    }
    private static Vector3d v3(JsonObject data,String key) {
        if(!data.has(key)) return new Vector3d();
        var v=vec(data.getAsJsonArray(key)); return new Vector3d(v.x,v.y,v.z);
    }
    private static Vec3 vec(JsonArray a) { return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()); }
    private static void require(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
}
