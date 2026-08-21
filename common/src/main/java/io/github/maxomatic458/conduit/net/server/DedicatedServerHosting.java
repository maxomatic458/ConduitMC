package io.github.maxomatic458.conduit.net.server;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.IrohThreads;
import net.minecraft.server.MinecraftServer;

/**
 * Hosting for dedicated servers
 */
public final class DedicatedServerHosting {

    private DedicatedServerHosting() {}

    /** Call once the server is accepting connections. */
    public static void onServerStarted(MinecraftServer server) {

        if (server.isSingleplayer()) {
            // Integrated servers publish on demand via Open to LAN, not at startup.
            return;
        }

        final IrohConfig config = IrohConfig.get();
        if (!config.shouldHostAutomatically()) {
            return;
        }
        if (!config.isEnabled()) {
            final String reason = IrohNet.unsupportedReason();
            Constants.LOG.warn("iroh hosting is unavailable{}", reason == null ? "" : ": " + reason);
            return;
        }

        // Off the main thread: binding waits for a relay, and the server should not stall booting.
        IrohThreads.start("publish", () -> {
            final String connectId = IrohServerHost.start(server.getPort());
            if (connectId != null) {
                Constants.LOG.info("=========================================================================");
                Constants.LOG.info(" Players can join this server without port forwarding using connect ID:");
                Constants.LOG.info("   {}", connectId);
                Constants.LOG.info(" This ID is derived from config/{}.json and is stable across restarts.", Constants.MOD_ID);
                Constants.LOG.info("=========================================================================");
            }
        });
    }

    /** Call as the server begins shutting down. */
    public static void onServerStopping(MinecraftServer server) {

        IrohServerHost.stop();
    }
}
