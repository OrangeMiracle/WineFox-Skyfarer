package dev.winefox.skyfarer.client.debug;

import com.github.tartaricacid.touhoulittlemaid.util.RenderHelper;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.runtime.DebugSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Locale;

@EventBusSubscriber(modid = WineFoxSkyfarer.MOD_ID, value = Dist.CLIENT)
public final class AirCombatDebugRenderer {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientDebugState.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance(); var data = ClientDebugState.current();
        if (mc.level == null || !data.hasUUID("maid") || !mc.level.dimension().location().toString().equals(data.getString("dimension"))) return;
        Vec3 position = DebugSnapshot.vector(data, "position").add(0, 0.8, 0);
        var entity = mc.level.getEntity(data.getInt("entity_id"));
        if (entity != null && entity.getUUID().equals(data.getUUID("maid"))) position = entity.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false)).add(0, 0.8, 0);
        var pose = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        if (data.contains("target")) RenderHelper.renderLine(pose, lines, position.subtract(camera), DebugSnapshot.vector(data, "target").subtract(camera), 1, 0.15f, 0.15f);
        var route = data.getList("route", Tag.TAG_COMPOUND); Vec3 previous = position;
        for (int i = 0; i < route.size(); i++) {
            Vec3 point = DebugSnapshot.vector(route.getCompound(i), "pos");
            RenderHelper.renderLine(pose, lines, previous.subtract(camera), point.subtract(camera), 0.15f, 0.4f, 1); previous = point;
        }
        var tube=data.getList("corridor",Tag.TAG_COMPOUND);Vec3[] last=null;
        for(int i=0;i<tube.size();i++) {
            var frame=tube.getCompound(i);Vec3 center=DebugSnapshot.vector(frame,"center");
            Vec3 tangent=i+1<tube.size()?DebugSnapshot.vector(tube.getCompound(i+1),"center").subtract(center):
                i>0?center.subtract(DebugSnapshot.vector(tube.getCompound(i-1),"center")):new Vec3(0,0,1);
            double length=tangent.horizontalDistance();
            Vec3 side=length<1.0e-6?new Vec3(1,0,0):new Vec3(tangent.z/length,0,-tangent.x/length);
            // AABB projection onto the cross-section conservatively encloses the collision box.
            double radius=frame.getDouble("radius")*(Math.abs(side.x)+Math.abs(side.z));
            Vec3[] corners={center.add(side.scale(radius)).add(0,frame.getDouble("above"),0),
                center.add(side.scale(-radius)).add(0,frame.getDouble("above"),0),
                center.add(side.scale(-radius)).add(0,-frame.getDouble("below"),0),
                center.add(side.scale(radius)).add(0,-frame.getDouble("below"),0)};
            for(int edge=0;edge<4;edge++) {
                if(last!=null)RenderHelper.renderLine(pose,lines,last[edge].subtract(camera),corners[edge].subtract(camera),1,.85f,.12f);
                if(i%6==0||i==tube.size()-1)RenderHelper.renderLine(pose,lines,corners[edge].subtract(camera),corners[(edge+1)%4].subtract(camera),1,.85f,.12f);
            }
            last=corners;
        }
        buffers.endBatch(RenderType.lines());
        if (data.contains("target")) label(event, I18n.get("debug.winefox_skyfarer.target"), DebugSnapshot.vector(data, "target").add(0, 1, 0), 0xFF4444, 0);
        if (!route.isEmpty()) label(event, I18n.get("debug.winefox_skyfarer.route"), DebugSnapshot.vector(route.getCompound(0), "pos").add(0, 1, 0), 0x4488FF, 0);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance(); var data = ClientDebugState.current();
        if (mc.level == null || mc.options.hideGui || !data.hasUUID("maid")
            || !mc.level.dimension().location().toString().equals(data.getString("dimension"))) return;
        String id = data.getUUID("maid").toString().substring(0, 8);
        String[] text = {
            "骑空士调试: " + data.getString("maid_name") + " [" + id + "]",
            "状态: " + data.getString("state"),
            "原因: " + data.getString("reason"),
            "扫描: " + data.getString("scan"),
            "本轮锁定（红线）: " + (data.contains("target_name") ? data.getString("target_name") : "尚未进入攻击航程"),
            "候选: " + data.getString("preferred_name") + String.format(Locale.ROOT, " | 分数 %.1f", data.getDouble("score")),
            String.format(Locale.ROOT, "速度 %.2f 格/t | 离地 %s 格", DebugSnapshot.vector(data, "velocity").length(), Double.isFinite(data.getDouble("clearance")) ? String.format(Locale.ROOT, "%.2f", data.getDouble("clearance")) : "未知"),
            String.format(Locale.ROOT, "Charge %s | 已用 %d / 前摇 %d ticks | 射程 %.2f", data.getBoolean("charging") ? "开启" : "关闭", data.getInt("charge_ticks"), data.getInt("charge_delay"), data.getDouble("spear_reach")),
            String.format(Locale.ROOT, "俯仰 %.1f° | 距目标 %.1f | 高于目标中心 %.1f", data.getDouble("pitch"), data.getDouble("target_distance"), data.getDouble("above_target")),
            String.format(Locale.ROOT, "转角 %.1f / %.0f° | 搜索 %.0f | 作战 %.0f", data.getDouble("angle"), data.getDouble("max_turn"), data.getDouble("search_range"), data.getDouble("combat_range")),
            "蓝线: 当前引导/剩余航点 | 再次右键同一女仆关闭"
        };
        var graphics = event.getGuiGraphics();
        int width = Math.max(100, Math.min(420, graphics.guiWidth() - 16));
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : text) lines.addAll(mc.font.split(Component.literal(line), width - 10));
        graphics.pose().pushPose();
        float scale = Math.min(1F, (graphics.guiHeight() - 16F) / (lines.size() * 10F + 10));
        graphics.pose().scale(scale, scale, 1);
        graphics.fill(5, 5, width + 5, 14 + lines.size() * 10, 0xB0101828);
        for (int i = 0; i < lines.size(); i++) graphics.drawString(mc.font, lines.get(i), 10, 10 + i * 10, i == 0 ? 0x80C0FF : 0xFFFFFF, true);
        graphics.pose().popPose();
    }
    private static void label(RenderLevelStageEvent event, String text, Vec3 position, int color, float offset) {
        RenderHelper.renderFloatingText(event.getPoseStack(), text, position.x, position.y, position.z, color, 0.025f, true, offset, true);
    }
}
