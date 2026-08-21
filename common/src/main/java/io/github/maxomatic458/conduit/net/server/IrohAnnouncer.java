package io.github.maxomatic458.conduit.net.server;

import io.github.maxomatic458.conduit.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

/**
 * Tells the host their connect ID once hosting is up.
 */
public final class IrohAnnouncer {

    private IrohAnnouncer() {}

    /** Broadcasts the connect ID */
    public static void announce(MinecraftServer server, String connectId) {

        if (connectId == null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(Constants.MOD_ID + ".host.failed").withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        final Component id = Component.literal(connectId).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(connectId))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(Constants.MOD_ID + ".host.copy"))));

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(Constants.MOD_ID + ".host.published", id).withStyle(ChatFormatting.GREEN),
                false);
    }
}
