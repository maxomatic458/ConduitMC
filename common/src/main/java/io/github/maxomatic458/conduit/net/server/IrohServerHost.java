package io.github.maxomatic458.conduit.net.server;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohHost;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.irohnet.IrohStream;
import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.IrohThreads;
import io.github.maxomatic458.conduit.net.StreamSplicer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Publishes the running server on the iroh network.
 */
public final class IrohServerHost {

    private static final Object LOCK = new Object();

    private static IrohHost host;
    private static int forwardPort;

    private IrohServerHost() {}

    /**
     * Starts hosting, forwarding inbound streams to {@code 127.0.0.1:mcPort}.
     *
     * @return the permanent connect ID, or {@code null} if hosting could not be started.
     */
    public static String start(int mcPort) {

        final IrohConfig config = IrohConfig.get();
        if (!config.isEnabled()) {
            final String reason = IrohNet.unsupportedReason();
            if (reason != null) {
                Constants.LOG.warn("Not hosting over iroh: {}", reason);
            }
            return null;
        }

        synchronized (LOCK) {
            if (host != null && host.isRunning()) {
                return host.endpointId();
            }
            try {
                host = IrohHost.open(config.secretKey(), config.endpointSettings());
            } catch (IOException e) {
                Constants.LOG.error("Could not start hosting over iroh", e);
                host = null;
                return null;
            }
            forwardPort = mcPort;
            IrohThreads.start("host-accept", IrohServerHost::acceptLoop);
            Constants.LOG.info("Hosting over iroh. Connect ID: {}", host.endpointId());
            return host.endpointId();
        }
    }

    /** The connect ID currently being advertised, or {@code null} when not hosting. */
    public static String connectId() {

        synchronized (LOCK) {
            return host != null && host.isRunning() ? host.endpointId() : null;
        }
    }

    public static boolean isHosting() {

        return connectId() != null;
    }

    public static void stop() {

        synchronized (LOCK) {
            if (host != null) {
                Constants.LOG.info("No longer hosting over iroh");
                host.close();
                host = null;
            }
        }
    }

    private static void acceptLoop() {

        final IrohHost current;
        final int port;
        synchronized (LOCK) {
            current = host;
            port = forwardPort;
        }
        if (current == null) {
            return;
        }

        while (true) {
            final IrohStream stream;
            try {
                stream = current.accept();
            } catch (IOException e) {
                Constants.LOG.error("iroh host stopped accepting connections", e);
                return;
            }
            if (stream == null) {
                return;
            }

            // One stream is one Minecraft connection with its own socket. Connected off-thread:
            // a stalled connect would otherwise hold up every other player's join for its timeout.
            final int forwardTo = port;
            IrohThreads.start("host-forward", () -> forward(stream, forwardTo));
        }
    }

    /** Splices one inbound stream onto a fresh loopback connection to the running server. */
    private static void forward(IrohStream stream, int port) {

        Socket socket = null;
        try {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 10_000);
            StreamSplicer.splice(socket, stream, "host");
        } catch (IOException e) {
            Constants.LOG.warn("Could not forward an iroh connection to 127.0.0.1:{}: {}", port, e.getMessage());
            // The socket owns a file descriptor even when connect() failed, so it has to be closed
            // here -- otherwise an unreachable server port leaks one per inbound attempt.
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Already gone; nothing to report.
                }
            }
            stream.close();
        }
    }
}
