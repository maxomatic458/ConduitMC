package io.github.maxomatic458.conduit.net.server;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.net.IrohConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.util.List;

public final class IrohConfigCommand {

    private static final String KEY = Constants.MOD_ID + ".command.";

    private IrohConfigCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // 26.2 replaced integer permission levels with PermissionCheck; Commands.hasPermission
        // adapts one into the Predicate that `requires` wants, which is how vanilla does it.
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Constants.MOD_ID + "-config")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(context -> show(context.getSource()));

        root.then(Commands.literal("enabled")
                .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                    final boolean value = BoolArgumentType.getBool(context, "value");
                    return apply(context.getSource(), "enabled", Boolean.toString(value),
                            config -> config.setEnabled(value));
                })));

        root.then(Commands.literal("hostAutomatically")
                .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                    final boolean value = BoolArgumentType.getBool(context, "value");
                    return apply(context.getSource(), "hostAutomatically", Boolean.toString(value),
                            config -> config.setHostAutomatically(value));
                })));

        root.then(Commands.literal("connectTimeoutSeconds")
                .then(Commands.argument("value", IntegerArgumentType.integer(5, 300)).executes(context -> {
                    final int value = IntegerArgumentType.getInteger(context, "value");
                    return apply(context.getSource(), "connectTimeoutSeconds", Integer.toString(value),
                            config -> config.setConnectTimeoutSeconds(value));
                })));

        root.then(Commands.literal("tunnelIdleTimeoutSeconds")
                .then(Commands.argument("value", IntegerArgumentType.integer(30, 86400)).executes(context -> {
                    final int value = IntegerArgumentType.getInteger(context, "value");
                    return apply(context.getSource(), "tunnelIdleTimeoutSeconds", Integer.toString(value),
                            config -> config.setTunnelIdleTimeoutSeconds(value));
                })));

        root.then(Commands.literal("relayUrls")
                .executes(context -> apply(context.getSource(), "relayUrls", "<defaults>",
                        config -> config.setRelayUrls(List.of())))
                .then(Commands.argument("urls", StringArgumentType.greedyString()).executes(context -> {
                    final List<String> urls = IrohConfig.parseRelayUrls(StringArgumentType.getString(context, "urls"));
                    // Reject bad input
                    final String problem = IrohNet.validateRelayUrls(urls);
                    if (problem != null) {
                        context.getSource().sendFailure(
                                Component.translatable(KEY + "bad_relay", problem).withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    return apply(context.getSource(), "relayUrls", String.join(", ", urls),
                            config -> config.setRelayUrls(urls));
                })));

        // Guarded by an explicit `confirm`
        root.then(Commands.literal("resetIdentity")
                .executes(context -> {
                    context.getSource().sendFailure(
                            Component.translatable(KEY + "reset_needs_confirm").withStyle(ChatFormatting.YELLOW));
                    return 0;
                })
                .then(Commands.literal("confirm").executes(context -> resetIdentity(context.getSource()))));

        dispatcher.register(root);
    }

    private static int resetIdentity(CommandSourceStack source) {

        final String fresh;
        try {
            fresh = IrohConfig.get().regenerateIdentity();
        } catch (IOException e) {
            source.sendFailure(Component.translatable(KEY + "reset_failed", String.valueOf(e.getMessage()))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(KEY + "reset_done").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("\n" + fresh).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(fresh))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable(Constants.MOD_ID + ".host.copy"))))), true);

        if (IrohServerHost.isHosting()) {
            source.sendSuccess(() -> Component.translatable(KEY + "reset_restart").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int apply(CommandSourceStack source, String option, String display, java.util.function.Consumer<IrohConfig> change) {

        final IrohConfig config = IrohConfig.get();
        change.accept(config);
        config.save();

        source.sendSuccess(() -> Component.translatable(KEY + "set", option, display).withStyle(ChatFormatting.GREEN), true);

        if (IrohServerHost.isHosting()) {
            source.sendSuccess(() -> Component.translatable(KEY + "restart_required").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int show(CommandSourceStack source) {

        final IrohConfig config = IrohConfig.get();
        final MutableComponent report = Component.empty()
                .append(Component.translatable(KEY + "header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (!IrohNet.isSupported()) {
            report.append("\n").append(Component.translatable(KEY + "unsupported", IrohNet.unsupportedReason())
                    .withStyle(ChatFormatting.RED));
        }

        final String connectId = config.connectId();
        report.append("\n").append(line("connectId"));
        if (connectId == null) {
            report.append(Component.translatable(KEY + "none").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            report.append(Component.literal(connectId).withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.CopyToClipboard(connectId))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable(Constants.MOD_ID + ".host.copy")))));
        }

        report.append("\n").append(line("hosting"))
                .append(value(IrohServerHost.isHosting() ? "yes" : "no"));
        report.append("\n").append(line("enabled")).append(value(Boolean.toString(config.isEnabledSetting())));
        report.append("\n").append(line("hostAutomatically")).append(value(Boolean.toString(config.shouldHostAutomatically())));
        report.append("\n").append(line("connectTimeoutSeconds")).append(value(Integer.toString(config.connectTimeoutSeconds())));
        report.append("\n").append(line("tunnelIdleTimeoutSeconds")).append(value(Integer.toString(config.tunnelIdleTimeoutSeconds())));
        report.append("\n").append(line("relayUrls")).append(value(config.relayUrls().isEmpty()
                ? "default (" + String.join(", ", IrohNet.defaultRelayUrls()) + ")"
                : String.join(", ", config.relayUrls())));
        report.append("\n").append(line("alpn")).append(value(config.alpn()));

        source.sendSuccess(() -> report, false);
        return 1;
    }

    private static MutableComponent line(String option) {

        return Component.literal("  " + option + ": ").withStyle(ChatFormatting.GRAY);
    }

    private static Component value(String text) {

        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }
}
