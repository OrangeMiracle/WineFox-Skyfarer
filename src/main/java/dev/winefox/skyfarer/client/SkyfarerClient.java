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
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SkyfarerNetwork.debug = dev.winefox.skyfarer.client.debug.ClientDebugState::accept;
        SkyfarerNetwork.openRemote = payload -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof RemoteMaidTaskScreen screen && screen.session().equals(payload.session())) screen.update(payload);
            else minecraft.setScreen(new RemoteMaidTaskScreen(payload));
        };
    }
}
