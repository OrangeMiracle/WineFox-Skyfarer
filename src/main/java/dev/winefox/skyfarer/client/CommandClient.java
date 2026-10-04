package dev.winefox.skyfarer.client;

import com.github.tartaricacid.touhoulittlemaid.util.RenderHelper;
import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.item.CommandSpyglassItem;
import dev.winefox.skyfarer.network.*;
import dev.winefox.skyfarer.runtime.DebugSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid=WineFoxSkyfarer.MOD_ID,value=Dist.CLIENT)
public final class CommandClient {
    private static CompoundTag data=new CompoundTag();private static boolean pressed;private static long received;
    public static void accept(CommandMarksPayload payload){data=payload.data()==null?new CompoundTag():payload.data();received=System.nanoTime();}
    private static boolean holding(){var p=Minecraft.getInstance().player;return p!=null&&(p.getMainHandItem().getItem() instanceof CommandSpyglassItem||p.getOffhandItem().getItem() instanceof CommandSpyglassItem);}
    @SubscribeEvent public static void input(ClientTickEvent.Pre event) {
        var mc=Minecraft.getInstance();boolean down=mc.options.keyAttack.isDown();
        if(mc.screen==null&&mc.player!=null&&mc.isWindowActive()&&holding()) {
            boolean clicked=false;while(mc.options.keyAttack.consumeClick())clicked=true;
            if(clicked||down&&!pressed)PacketDistributor.sendToServer(new ToggleCommandMarkPayload());
        }
        pressed=down;
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event){if(event.isAttack()&&holding()){event.setCanceled(true);event.setSwingHand(false);}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){data=new CompoundTag();pressed=false;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||mc.level==null||mc.player==null||mc.options.hideGui||System.nanoTime()-received>3_000_000_000L)return;
        if(!mc.level.dimension().location().toString().equals(data.getString("dimension")))return;
        var marks=data.getList("marks",Tag.TAG_COMPOUND);var buffers=mc.renderBuffers().bufferSource();
        Vec3 camera=event.getCamera().getPosition();Vector3f axis=new Vector3f(1,0,0).rotate(event.getCamera().rotation());Vec3 right=new Vec3(axis.x,axis.y,axis.z);
        for(int i=0;i<Math.min(5,marks.size());i++) {
            var mark=marks.getCompound(i);Vec3 feet=DebugSnapshot.vector(mark,"position");double height=mark.getDouble("height"),width=mark.getDouble("width");
            var entity=mc.level.getEntity(mark.getInt("entity"));if(entity!=null&&mark.hasUUID("id")&&entity.getUUID().equals(mark.getUUID("id")))feet=entity.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            Vec3 center=feet.add(0,height*.5,0),head=feet.add(0,height+.45,0);
            boolean visible=mc.level.clip(new ClipContext(camera,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS;
            RenderHelper.renderFloatingText(event.getPoseStack(),"◇",head.x,head.y,head.z,0xFF7838,.04f,true,0,true);
            if(!visible)continue; // Through walls: only the hollow diamond, no number or brackets.
            RenderHelper.renderFloatingText(event.getPoseStack(),Integer.toString(i+1),head.x,head.y+.3,head.z,0xFF7838,.025f,true,0,false);
            var lines=buffers.getBuffer(RenderType.lines());
            double half=Math.max(.35,width*.65),tall=height*.55;
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}) {
                Vec3 corner=center.add(right.scale(x*half)).add(0,y*tall,0);
                RenderHelper.renderLine(event.getPoseStack(),lines,corner.subtract(camera),corner.subtract(right.scale(x*half*.4)).subtract(camera),1,.42f,.12f);
                RenderHelper.renderLine(event.getPoseStack(),lines,corner.subtract(camera),corner.add(0,-y*Math.min(.3,tall*.4),0).subtract(camera),1,.42f,.12f);
            }
            buffers.endBatch(RenderType.lines());
        }buffers.endBatch(RenderType.lines());
    }
}
