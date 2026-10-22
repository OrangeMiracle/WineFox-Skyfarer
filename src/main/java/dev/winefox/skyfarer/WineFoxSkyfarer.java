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
    public static final DeferredItem<dev.winefox.skyfarer.item.CometHeartItem> COMET_HEART=ITEMS.register("comet_heart",()->new dev.winefox.skyfarer.item.CometHeartItem(false));
    public static final DeferredItem<dev.winefox.skyfarer.item.CometHeartItem> EVERBURNING_COMET_HEART=ITEMS.register("everburning_comet_heart",()->new dev.winefox.skyfarer.item.CometHeartItem(true));
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS=DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,MOD_ID);
    static {LOOT_MODIFIERS.register("comet_heart",()->dev.winefox.skyfarer.item.CometLootModifier.CODEC);}
    public static final DeferredItem<dev.winefox.skyfarer.item.MaidRemoteItem> MAID_REMOTE = ITEMS.register("maid_remote", () -> new dev.winefox.skyfarer.item.MaidRemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.winefox.skyfarer.item.DebugStickItem> DEBUG_STICK = ITEMS.register("debug_stick", () -> new dev.winefox.skyfarer.item.DebugStickItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.winefox.skyfarer.item.CommandSpyglassItem> COMMAND_SPYGLASS=ITEMS.register("command_spyglass",()->new dev.winefox.skyfarer.item.CommandSpyglassItem(new Item.Properties().stacksTo(1),false));
    public static final DeferredItem<dev.winefox.skyfarer.item.CommandSpyglassItem> CREATIVE_COMMAND_SPYGLASS=ITEMS.register("creative_command_spyglass",()->new dev.winefox.skyfarer.item.CommandSpyglassItem(new Item.Properties().stacksTo(1),true));

    public WineFoxSkyfarer(IEventBus bus, ModContainer container) {
        ITEMS.register(bus);
        dev.winefox.skyfarer.weapon.Fox3Entities.TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.weapon.Fox3System::tick);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.weapon.Fox3System::leave);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->dev.winefox.skyfarer.weapon.Fox3System.clear());
        dev.winefox.skyfarer.network.CometParticles.TYPES.register(bus);
        LOOT_MODIFIERS.register(bus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{
            if(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)dev.winefox.skyfarer.runtime.CometRelicDrops.refresh(item);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event)->{
            if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)dev.winefox.skyfarer.runtime.CometRewards.get(player.server).deliver(player);
        });
        dev.winefox.skyfarer.flight.PredictionHorizon.multiplier=()->dev.winefox.skyfarer.config.SkyfarerSettings.get("prediction_length_multiplier");
        dev.winefox.skyfarer.command.CommandManager.install();
        bus.addListener(dev.winefox.skyfarer.command.CommandTickets::register);
        bus.addListener(dev.winefox.skyfarer.runtime.DebugPanelManager::registerTickets);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.command.CommandManager::tick);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.command.CommandManager::died);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{if(event.getEntity() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid&&!event.getLevel().isClientSide())dev.winefox.skyfarer.command.CommandManager.observe(maid);});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->dev.winefox.skyfarer.command.CommandManager.logout(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->dev.winefox.skyfarer.command.CommandManager.clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->dev.winefox.skyfarer.runtime.DebugPanelManager.clear());
        bus.addListener(this::creativeTab);
        bus.addListener(dev.winefox.skyfarer.network.SkyfarerNetwork::register);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.runtime.DebugWatchManager::tick);
        NeoForge.EVENT_BUS.addListener(dev.winefox.skyfarer.runtime.SkyfarerRuntime::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event)->dev.winefox.skyfarer.async.PlannerSession.serverTick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->dev.winefox.skyfarer.async.PlannerSession.stop());
        bus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event)->dev.winefox.skyfarer.config.SkyfarerSettings.reloaded());
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
            event.accept(COMMAND_SPYGLASS);event.accept(CREATIVE_COMMAND_SPYGLASS);
            event.accept(COMET_HEART);event.accept(EVERBURNING_COMET_HEART);
        }
    }
}
