package dev.winefox.skyfarer.client;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.client.gui.RemoteMaidTaskScreen;
import dev.winefox.skyfarer.network.SkyfarerNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = WineFoxSkyfarer.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SkyfarerClient {
    @SubscribeEvent public static void entities(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.winefox.skyfarer.weapon.Fox3Entities.MISSILE.get(), net.minecraft.client.renderer.entity.FireworkEntityRenderer::new);
    }
    @SubscribeEvent public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event){
        event.registerSpriteSet(dev.winefox.skyfarer.network.CometParticles.TAIL.get(),sprites->(type,level,x,y,z,vx,vy,vz)->new CometGlowParticle(level,x,y,z,vx,vy,vz,sprites,false));
        event.registerSpriteSet(dev.winefox.skyfarer.network.CometParticles.RING.get(),sprites->(type,level,x,y,z,vx,vy,vz)->new CometGlowParticle(level,x,y,z,vx,vy,vz,sprites,true));
    }
    public static final net.minecraft.client.KeyMapping DEBUG_PREVIOUS=new net.minecraft.client.KeyMapping("key.winefox_skyfarer.debug_previous",com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT,"key.categories.winefox_skyfarer");
    public static final net.minecraft.client.KeyMapping DEBUG_NEXT=new net.minecraft.client.KeyMapping("key.winefox_skyfarer.debug_next",com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,"key.categories.winefox_skyfarer");
    @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){event.register(DEBUG_PREVIOUS);event.register(DEBUG_NEXT);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SkyfarerNetwork.cometThrust=CometThrustParticles::accept;
        SkyfarerNetwork.debug = dev.winefox.skyfarer.client.debug.ClientDebugState::accept;
        SkyfarerNetwork.marks=CommandClient::accept;
        SkyfarerNetwork.debugPanel=payload->{
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof dev.winefox.skyfarer.client.gui.DebugMaidScreen screen&&screen.session().equals(payload.data().getUUID("session")))screen.update(payload.data());
            else if(payload.data().getBoolean("open"))mc.setScreen(new dev.winefox.skyfarer.client.gui.DebugMaidScreen(payload.data()));
        };
        SkyfarerNetwork.openRemote = payload -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof RemoteMaidTaskScreen screen && screen.session().equals(payload.session())) screen.update(payload);
            else minecraft.setScreen(new RemoteMaidTaskScreen(payload));
        };
    }
}
