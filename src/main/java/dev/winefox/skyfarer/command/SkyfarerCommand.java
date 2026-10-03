package dev.winefox.skyfarer.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.winefox.skyfarer.config.SkyfarerConfig;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SkyfarerCommand {
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(message -> Component.literal(message.toString()));
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfs")
            .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("WineFox: Skyfarer — /wfs config list|get|set|reset | /wfs debuglog on|off|status"), false); return 1; })
            .then(Commands.literal("debuglog").requires(source -> source.hasPermission(2))
                .executes(SkyfarerCommand::reportDebug)
                .then(Commands.literal("status").executes(SkyfarerCommand::reportDebug))
                .then(Commands.literal("on").executes(ctx -> setDebug(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setDebug(ctx, false))))
            .then(Commands.literal("config").requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    SkyfarerSettings.snapshot().forEach((name, value) -> report(ctx, name, value)); return reportDebug(ctx);
                }))
                .then(Commands.literal("get")
                    .then(Commands.literal("debuglog").executes(SkyfarerCommand::reportDebug))
                    .then(parameter().executes(ctx -> apply(ctx, "get"))))
                .then(Commands.literal("set")
                    .then(Commands.literal("debuglog").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> setDebug(ctx, BoolArgumentType.getBool(ctx, "enabled")))))
                    .then(parameter().then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> apply(ctx, "set")))))
                .then(Commands.literal("reset")
                    .then(Commands.literal("debuglog").executes(ctx -> setDebug(ctx, false)))
                    .then(Commands.literal("all").executes(ctx -> { SkyfarerSettings.reset("all"); ctx.getSource().sendSuccess(() -> Component.literal("Reset all Skyfarer parameters"), true); return 1; }))
                    .then(parameter().executes(ctx -> apply(ctx, "reset"))))));
    }
    private static int setDebug(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        try { SkyfarerSettings.setDebugLogging(enabled); return reportDebug(ctx); }
        catch (IllegalArgumentException exception) { throw ERROR.create(exception.getMessage()); }
    }
    private static int reportDebug(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("debuglog = " + SkyfarerSettings.debugLogging() + " (WineFoxSkyfarer / latest.log)"), false);
        return 1;
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> parameter() {
        return Commands.argument("parameter", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SkyfarerConfig.PARAMETERS.keySet(), builder));
    }
    private static int apply(CommandContext<CommandSourceStack> ctx, String operation) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "parameter");
        try {
            if (operation.equals("set")) SkyfarerSettings.set(name, DoubleArgumentType.getDouble(ctx, "value"));
            if (operation.equals("reset")) SkyfarerSettings.reset(name);
            report(ctx, name, SkyfarerSettings.get(name)); return 1;
        } catch (IllegalArgumentException exception) { throw ERROR.create(exception.getMessage()); }
    }
    private static void report(CommandContext<CommandSourceStack> ctx, String name, double value) {
        var parameter = SkyfarerConfig.PARAMETERS.get(name);
        ctx.getSource().sendSuccess(() -> Component.literal(name + " = " + value + " " + parameter.unit()), false);
    }
}
