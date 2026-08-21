package io.github.maxomatic458.conduit.net.client;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohClient;
import io.github.maxomatic458.conduit.irohnet.IrohConnectionInfo;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.irohnet.IrohPeer;
import io.github.maxomatic458.conduit.irohnet.IrohStream;
import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.IrohThreads;
import io.github.maxomatic458.conduit.net.StreamSplicer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side tunnels, one per host connect ID. Each listens on a loopback port and dials the host
 * on first use. They are cached because the multiplayer screen pings a server and then joins it,
 * and both must land on the same host without redialling.
 */
public final class IrohClientTunnels {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final Map<String, Tunnel> TUNNELS = new HashMap<>();
    private static final Object LOCK = new Object();
    private static IrohClient client;
    private static ScheduledExecutorService reaper;

    private IrohClientTunnels() {}

    /** Drops tunnels that have gone idle, and the shared endpoint once none are left. */
    private static void reapIdleTunnels() {

        synchronized (LOCK) {
            // isAlive() closes the tunnel itself when it has expired.
            TUNNELS.values().removeIf(tunnel -> !tunnel.isAlive());
            if (TUNNELS.isEmpty() && client != null) {
                client.close();
                client = null;
            }
        }
    }

    /** Get the loopback address for a connectID */
    public static InetSocketAddress addressFor(String connectId) throws IOException {

        final String id = IrohNet.normalizeEndpointId(connectId);
        synchronized (LOCK) {
            Tunnel tunnel = TUNNELS.get(id);
            if (tunnel != null && tunnel.isAlive()) {
                tunnel.touch();
                return tunnel.localAddress();
            }
            if (tunnel != null) {
                tunnel.close();
            }
            tunnel = new Tunnel(id);
            TUNNELS.put(id, tunnel);
            if (reaper == null) {
                reaper = Executors.newSingleThreadScheduledExecutor(IrohThreads.factory("reaper"));
                reaper.scheduleWithFixedDelay(IrohClientTunnels::reapIdleTunnels, 60, 60, TimeUnit.SECONDS);
            }
            Constants.LOG.info("Tunnelling {} via 127.0.0.1:{}", id, tunnel.localAddress().getPort());
            return tunnel.localAddress();
        }
    }

    private static IrohClient client() throws IOException {

        synchronized (LOCK) {
            if (client == null) {
                client = IrohClient.open(IrohConfig.get().endpointSettings());
            }
            return client;
        }
    }

    /** This client's own connect ID, or {@code null} if no endpoint exists yet. Never creates one. */
    public static String localEndpointId() {

        synchronized (LOCK) {
            return client == null ? null : client.endpointId();
        }
    }

    public static List<IrohTunnelStatus> statuses() {

        synchronized (LOCK) {
            final List<IrohTunnelStatus> statuses = new ArrayList<>();
            for (Tunnel tunnel : TUNNELS.values()) {
                if (!tunnel.closed) {
                    statuses.add(tunnel.status());
                }
            }
            return statuses;
        }
    }

    public record IrohTunnelStatus(String connectId, int localPort, IrohConnectionInfo connection) {}

    /** Shuts down all tunnels and the client */
    public static void shutdown() {

        synchronized (LOCK) {
            TUNNELS.values().forEach(Tunnel::close);
            TUNNELS.clear();
            if (client != null) {
                client.close();
                client = null;
            }
        }
    }

    private static final class Tunnel {

        private final String connectId;
        private final ServerSocket listener;
        private final InetSocketAddress localAddress;
        private final AtomicInteger activeConnections = new AtomicInteger();

        private volatile long lastUsed = System.currentTimeMillis();
        private volatile boolean closed;

        /** Volatile so status() can read it without the monitor, which connect() holds while dialling. */
        private volatile IrohPeer peer;

        Tunnel(String connectId) throws IOException {

            this.connectId = connectId;
            this.listener = new ServerSocket(0, 16, LOOPBACK);
            this.localAddress = new InetSocketAddress(LOOPBACK, listener.getLocalPort());
            IrohThreads.start("accept", this::acceptLoop);
        }

        InetSocketAddress localAddress() {

            return localAddress;
        }

        void touch() {

            lastUsed = System.currentTimeMillis();
        }

        boolean isAlive() {

            if (closed || listener.isClosed()) {
                return false;
            }
            // A player mid-game opens no new connections, so reaping on idle time alone would
            // close the peer and drop them from the server.
            if (activeConnections.get() > 0) {
                touch();
                return true;
            }
            if (System.currentTimeMillis() - lastUsed > IrohConfig.get().tunnelIdleTimeoutMillis()) {
                Constants.LOG.debug("Retiring idle iroh tunnel for {}", connectId);
                close();
                return false;
            }
            return true;
        }

        private void acceptLoop() {

            while (!closed) {
                final Socket socket;
                try {
                    socket = listener.accept();
                } catch (IOException e) {
                    if (!closed) {
                        Constants.LOG.debug("iroh tunnel listener for {} stopped", connectId, e);
                    }
                    return;
                }
                touch();
                // Off-thread so a slow handshake cannot stall the next connection.
                IrohThreads.start("dial", () -> openStream(socket));
            }
        }

        private void openStream(Socket socket) {

            try {
                socket.setTcpNoDelay(true);
                // Resolve the endpoint before taking this tunnel's monitor: connect() would
                // otherwise hold it while waiting on LOCK, deadlocking against addressFor().
                final IrohClient endpoint = client();
                final IrohStream stream = connect(endpoint).openStream();
                activeConnections.incrementAndGet();
                StreamSplicer.splice(socket, stream, "client", () -> {
                    activeConnections.decrementAndGet();
                    touch();
                });
            } catch (IOException e) {
                Constants.LOG.warn("Could not open an iroh stream to {}: {}", connectId, e.getMessage());
                // Vanilla then reports it as a normal connection failure.
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        IrohTunnelStatus status() {

            IrohConnectionInfo info = null;
            final IrohPeer current = peer;
            if (current != null && current.isAlive()) {
                try {
                    info = current.info();
                } catch (IOException e) {
                    Constants.LOG.debug("Could not read iroh connection info for {}", connectId, e);
                }
            }
            return new IrohTunnelStatus(connectId, localAddress.getPort(), info);
        }

        private synchronized IrohPeer connect(IrohClient endpoint) throws IOException {

            if (peer != null && peer.isAlive()) {
                return peer;
            }
            if (peer != null) {
                peer.close();
                peer = null;
            }
            Constants.LOG.info("Connecting to {} over iroh...", connectId);
            peer = endpoint.connect(connectId, IrohConfig.get().connectTimeoutMillis());
            Constants.LOG.info("Connected to {} over iroh", connectId);
            return peer;
        }

        synchronized void close() {

            if (closed) {
                return;
            }
            closed = true;
            try {
                listener.close();
            } catch (IOException e) {
                Constants.LOG.debug("Failed to close tunnel listener", e);
            }
            if (peer != null) {
                peer.close();
                peer = null;
            }
        }
    }
}
