package dev.winefox.skyfarer.network;

import dev.winefox.skyfarer.runtime.RemoteSessions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

public final class SkyfarerNetwork {
    // Installed only by the client entry point; common code never links a Screen/Minecraft class.
    public static Consumer<OpenRemoteTaskPayload> openRemote = payload -> {};
    public static Consumer<AirCombatDebugPayload> debug = payload -> {};
    public static Consumer<CommandMarksPayload> marks=payload->{};
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(CommandMarksPayload.TYPE,CommandMarksPayload.CODEC,(payload,context)->context.enqueueWork(()->marks.accept(payload)));
        registrar.playToServer(ToggleCommandMarkPayload.TYPE,ToggleCommandMarkPayload.CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)dev.winefox.skyfarer.command.CommandManager.toggle(player);}));
        registrar.playToClient(AirCombatDebugPayload.TYPE, AirCombatDebugPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> debug.accept(payload)));
        registrar.playToClient(OpenRemoteTaskPayload.TYPE, OpenRemoteTaskPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> openRemote.accept(payload)));
        registrar.playToServer(SetRemoteMaidTaskPayload.TYPE, SetRemoteMaidTaskPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) RemoteSessions.setTask(player, payload);
            }));
    }
}
