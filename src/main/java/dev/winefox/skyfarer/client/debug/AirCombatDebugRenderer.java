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
    @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        while(dev.winefox.skyfarer.client.SkyfarerClient.DEBUG_PREVIOUS.consumeClick())if(mc.screen==null&&mc.isWindowActive())ClientDebugState.selection.cycle(-1);
        while(dev.winefox.skyfarer.client.SkyfarerClient.DEBUG_NEXT.consumeClick())if(mc.screen==null&&mc.isWindowActive())ClientDebugState.selection.cycle(1);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientDebugState.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.options.hideGui)return;
        for(var data:ClientDebugState.all())renderMaid(event,data);
    }
    private static void renderMaid(RenderLevelStageEvent event,net.minecraft.nbt.CompoundTag data){
        var mc = Minecraft.getInstance();
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
        var curve=data.getList("attack_curve",Tag.TAG_COMPOUND);
        for(int i=1;i<curve.size();i++)RenderHelper.renderLine(pose,lines,
            DebugSnapshot.vector(curve.getCompound(i-1),"pos").subtract(camera),
            DebugSnapshot.vector(curve.getCompound(i),"pos").subtract(camera),.15f,1f,.9f);
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
        if(data.contains("attack_entry")){
            Vec3 center=DebugSnapshot.vector(data,"attack_entry");var entryLines=buffers.getBuffer(RenderType.lines());
            for(int axis=0;axis<3;axis++)for(int a:new int[]{-1,1})for(int b:new int[]{-1,1}){
                Vec3 from=axis==0?new Vec3(-.5,a*.5,b*.5):axis==1?new Vec3(a*.5,-.5,b*.5):new Vec3(a*.5,b*.5,-.5);
                Vec3 along=axis==0?new Vec3(1,0,0):axis==1?new Vec3(0,1,0):new Vec3(0,0,1);
                RenderHelper.renderLine(pose,entryLines,center.add(from).subtract(camera),center.add(from).add(along).subtract(camera),.2f,1,.35f);
            }
            RenderHelper.renderLine(pose,entryLines,center.subtract(camera),center.add(DebugSnapshot.vector(data,"entry_direction").scale(2)).subtract(camera),.2f,1,.35f);
            buffers.endBatch(RenderType.lines());
        }
        if(data.getUUID("maid").equals(ClientDebugState.selection.selected())){
            var selectedLines=buffers.getBuffer(DebugRenderTypes.SELECTED);
            double h=Math.max(.8,data.getDouble("height")),half=Math.max(.35,data.getDouble("width")*.65);
            Vec3 center=position.add(0,h*.5-.8,0);
            var axis=new org.joml.Vector3f(1,0,0).rotate(event.getCamera().rotation());Vec3 right=new Vec3(axis.x,axis.y,axis.z);
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}){
                Vec3 corner=center.add(right.scale(x*half)).add(0,y*h*.55,0);
                RenderHelper.renderLine(pose,selectedLines,corner.subtract(camera),corner.subtract(right.scale(x*half*.4)).subtract(camera),.3f,1,.4f);
                RenderHelper.renderLine(pose,selectedLines,corner.subtract(camera),corner.add(0,-y*Math.min(.3,h*.22),0).subtract(camera),.3f,1,.4f);
            }buffers.endBatch(DebugRenderTypes.SELECTED);
        }
        if (data.contains("target")) label(event, I18n.get("debug.winefox_skyfarer.target"), DebugSnapshot.vector(data, "target").add(0, 1, 0), 0xFF4444, 0);
        if (!route.isEmpty()) label(event, I18n.get("debug.winefox_skyfarer.route"), DebugSnapshot.vector(route.getCompound(0), "pos").add(0, 1, 0), 0x4488FF, 0);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance(); var data = ClientDebugState.current();
        if (mc.level == null || mc.options.hideGui || mc.screen instanceof dev.winefox.skyfarer.client.gui.DebugMaidScreen || !data.hasUUID("maid")) return;
        String id = data.getUUID("maid").toString().substring(0, 8);
        String[] text = {
            "骑空士调试: " + data.getString("maid_name") + " [" + id + "]",
            "维度: "+data.getString("dimension")+" | "+(ClientDebugState.selection.ids().indexOf(ClientDebugState.selection.selected())+1)+" / "+ClientDebugState.selection.ids().size(),
            "状态: " + data.getString("state"),
            "原因: " + data.getString("reason")+(data.getString("curve_style").isEmpty()?"":" | "+data.getString("curve_style")),
            "扫描: " + data.getString("scan"),
            "异步规划: " + data.getString("planner_status"),
            "本轮锁定（红线）: " + (data.contains("target_name") ? data.getString("target_name") : "尚未进入攻击航程"),
            "候选: " + data.getString("preferred_name") + String.format(Locale.ROOT, " | 分数 %.1f", data.getDouble("score")),
            String.format(Locale.ROOT, "速度 %.2f 格/t | 离地 %s 格", DebugSnapshot.vector(data, "velocity").length(), Double.isFinite(data.getDouble("clearance")) ? String.format(Locale.ROOT, "%.2f", data.getDouble("clearance")) : "未知"),
            String.format(Locale.ROOT, "Charge %s | 已用 %d / 前摇 %d ticks | 射程 %.2f", data.getBoolean("charging") ? "开启" : "关闭", data.getInt("charge_ticks"), data.getInt("charge_delay"), data.getDouble("spear_reach")),
            String.format(Locale.ROOT, "俯仰 %.1f° | 距目标 %.1f | 高于目标中心 %.1f", data.getDouble("pitch"), data.getDouble("target_distance"), data.getDouble("above_target")),
            String.format(Locale.ROOT, "转角 %.1f / %.0f° | 搜索 %.0f | 作战 %.0f", data.getDouble("angle"), data.getDouble("max_turn"), data.getDouble("search_range"), data.getDouble("combat_range")),
            "蓝线: 引导航迹 | 青绿线: 拟合攻击曲线 | 绿方块: 攻击入口 | 方向键 ←/→: 切换女仆"
        };
        var graphics = event.getGuiGraphics();
        int width = Math.max(100, Math.min(420, graphics.guiWidth() - 16));
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (int i=0;i<text.length;i++){
            var component=Component.literal(text[i]);
            if(i==0){component=Component.literal("骑空士调试: "+data.getString("maid_name")).withStyle(s->s.withColor(0x80C0FF));if(data.getBoolean("unloaded"))component.append(Component.literal("（已卸载）").withStyle(s->s.withColor(0xFFD070)));component.append(" ["+id+"]");}
            lines.addAll(mc.font.split(component,width-10));
        }
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
