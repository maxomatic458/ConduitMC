package io.github.maxomatic458.conduit.net.client;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohConnectionInfo;
import io.github.maxomatic458.conduit.irohnet.IrohConnectionMode;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.irohnet.IrohPathInfo;
import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.client.IrohClientTunnels.IrohTunnelStatus;
import io.github.maxomatic458.conduit.net.server.IrohServerHost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;

/**
 * Builds the chat report shown by {@code /conduit-info}.
 *
 * <p>Lives in common and returns a {@link Component} because the two loaders hand out different
 * command source types -- only the registration differs, never the content.
 */
public final class IrohInfoReport {

    private static final String KEY = Constants.MOD_ID + ".irohinfo.";

    private IrohInfoReport() {}

    public static Component build() {

        final MutableComponent report = Component.empty()
                .append(Component.translatable(KEY + "header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (!IrohNet.isSupported()) {
            return report.append("\n").append(Component.translatable(KEY + "unsupported", IrohNet.unsupportedReason())
                    .withStyle(ChatFormatting.RED));
        }
        if (!IrohConfig.get().isEnabled()) {
            return report.append("\n").append(Component.translatable(KEY + "disabled").withStyle(ChatFormatting.YELLOW));
        }

        appendIdentity(report);
        appendHosting(report);
        appendTunnels(report);
        return report;
    }

    private static void appendIdentity(MutableComponent report) {

        final String localId = IrohClientTunnels.localEndpointId();
        report.append("\n").append(label(KEY + "local_id"));
        if (localId == null) {
            // No outbound endpoint exists until the first iroh join
            report.append(Component.translatable(KEY + "local_id.inactive").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            report.append(copyableId(localId));
        }
    }

    private static void appendHosting(MutableComponent report) {

        final String hostId = IrohServerHost.connectId();
        report.append("\n").append(label(KEY + "hosting"));
        if (hostId == null) {
            report.append(Component.translatable(KEY + "hosting.inactive").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            report.append(copyableId(hostId));
        }
    }

    private static void appendTunnels(MutableComponent report) {

        final List<IrohTunnelStatus> statuses = IrohClientTunnels.statuses();
        report.append("\n").append(label(KEY + "connections"));

        if (statuses.isEmpty()) {
            report.append(Component.translatable(KEY + "connections.none").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        report.append(Component.literal(Integer.toString(statuses.size())).withStyle(ChatFormatting.WHITE));

        for (IrohTunnelStatus status : statuses) {
            report.append("\n  ").append(copyableId(status.connectId()));
            report.append(Component.literal(" (127.0.0.1:" + status.localPort() + ")").withStyle(ChatFormatting.DARK_GRAY));

            final IrohConnectionInfo connection = status.connection();
            if (connection == null) {
                report.append("\n    ").append(Component.translatable(KEY + "not_connected").withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }

            final String modeKey = KEY + "mode." + connection.getMode().name().toLowerCase(Locale.ROOT);
            report.append("\n    ").append(label(KEY + "mode"))
                    .append(Component.translatable(modeKey).withStyle(modeColour(connection.getMode()), ChatFormatting.BOLD))
                    .append(Component.literal(" "))
                    .append(Component.translatable(modeKey + ".hint").withStyle(ChatFormatting.DARK_GRAY));
            if (connection.getRttMillis() >= 0) {
                report.append(Component.literal("  " + connection.getRttMillis() + " ms").withStyle(ChatFormatting.GRAY));
            }
            report.append(Component.literal("  ↑" + bytes(connection.getBytesSent())
                            + " ↓" + bytes(connection.getBytesReceived()))
                    .withStyle(ChatFormatting.DARK_GRAY));

            for (IrohPathInfo path : connection.getPaths()) {
                report.append("\n      ").append(path(path));
            }
        }
    }

    private static Component path(IrohPathInfo path) {
        final MutableComponent line = Component.literal(path.getSelected() ? "● " : "○ ")
                .withStyle(path.getSelected() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);

        line.append(Component.translatable(path.getRelay() ? KEY + "path.relay" : KEY + "path.direct")
                .withStyle(path.getRelay() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        line.append(Component.literal(" " + path.getRemoteAddress()).withStyle(ChatFormatting.GRAY));
        if (path.getRttMillis() > 0) {
            line.append(Component.literal(" " + path.getRttMillis() + " ms").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static ChatFormatting modeColour(IrohConnectionMode mode) {

        return switch (mode) {
            case DIRECT -> ChatFormatting.GREEN;
            case MIXED -> ChatFormatting.AQUA;
            case RELAYED -> ChatFormatting.YELLOW;
            case CONNECTING -> ChatFormatting.DARK_GRAY;
        };
    }

    private static MutableComponent label(String key) {

        return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(": "));
    }

    /** Connect IDs are 64 hex chars, so show a short form and put the full value on the clipboard. */
    private static Component copyableId(String id) {

        return Component.literal(id.substring(0, 12) + "…").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(id))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal(id).append("\n").append(Component.translatable(KEY + "copy")))));
    }

    private static String bytes(long value) {

        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", value / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0));
    }
}
