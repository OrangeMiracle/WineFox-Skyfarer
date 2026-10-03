package dev.winefox.skyfarer;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(WineFoxSkyfarer.MOD_ID)
public final class WineFoxSkyfarer {
    public static final String MOD_ID = "winefox_skyfarer";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<dev.winefox.skyfarer.item.MaidRemoteItem> MAID_REMOTE = ITEMS.register("maid_remote", () -> new dev.winefox.skyfarer.item.MaidRemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.winefox.skyfarer.item.DebugStickItem> DEBUG_STICK = ITEMS.register("debug_stick", () -> new dev.winefox.skyfarer.item.DebugStickItem(new Item.Properties().stacksTo(1)));

    public WineFoxSkyfarer(IEventBus bus, ModContainer container) {
        ITEMS.register(bus);
        bus.addListener(this::creativeTab);
        bus.addListener(dev.winefox.skyfarer.network.SkyfarerNetwork::register);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.runtime.DebugWatchManager::tick);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.runtime.SkyfarerRuntime::tick);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.runtime.SkyfarerRuntime::leave);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> dev.winefox.skyfarer.runtime.SkyfarerRuntime.clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> dev.winefox.skyfarer.runtime.DebugWatchManager.logout(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> dev.winefox.skyfarer.runtime.DebugWatchManager.clearAll());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> dev.winefox.skyfarer.runtime.RemoteSessions.close(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> dev.winefox.skyfarer.runtime.RemoteSessions.clear());
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, dev.winefox.skyfarer.config.SkyfarerConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.command.SkyfarerCommand::register);
        LOGGER.info("WineFox: Skyfarer initializing; required dependencies: Touhou Little Maid and Spears");
    }
    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MAID_REMOTE); event.accept(DEBUG_STICK);
        }
    }
}
