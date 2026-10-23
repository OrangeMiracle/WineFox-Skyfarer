package dev.winefox.skyfarer.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.winefox.skyfarer.weapon.Fox3MissileEntity;
import dev.winefox.skyfarer.weapon.Fox3VisualOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Static cube/UV bake retains the artist's nested pivots, per-face UVs and original PNG. */
public final class Fox3MissileRenderer extends EntityRenderer<Fox3MissileEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("winefox_skyfarer","textures/entity/fox_tail_missile.png");
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("winefox_skyfarer","models/entity/fox_tail_missile.json");
    private record Face(float[][] vertices, float[] normal) {}
    private final List<Face> faces = new ArrayList<>();
    public Fox3MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(MODEL)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : data.getAsJsonArray("faces")) {
                var face = entry.getAsJsonObject(); var vertices = new float[4][5]; var normal = new float[3];
                for(int i=0;i<4;i++) for(int j=0;j<5;j++) vertices[i][j]=face.getAsJsonArray("vertices").get(i).getAsJsonArray().get(j).getAsFloat();
                for(int i=0;i<3;i++) normal[i]=face.getAsJsonArray("normal").get(i).getAsFloat();
                faces.add(new Face(vertices,normal));
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot load Fox3 tail model " + MODEL,e); }
    }
    @Override public void render(Fox3MissileEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose();
        poses.mulPose(Fox3VisualOrientation.rotation(entity.visualYaw(partial),entity.visualPitch(partial)));
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var pose = poses.last();
        for(var face:faces) for(var v:face.vertices)
            consumer.addVertex(pose,v[0],v[1],v[2]).setColor(255,255,255,255).setUv(v[3],v[4])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,face.normal[0],face.normal[1],face.normal[2]);
        poses.popPose();
        super.render(entity,yaw,partial,poses,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(Fox3MissileEntity entity) { return TEXTURE; }
}
