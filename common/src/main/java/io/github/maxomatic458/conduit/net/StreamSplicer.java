package io.github.maxomatic458.conduit.net;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends and receives bytes between a local TCP socket and the iroh stream (emulating a player connection.)
 */
public final class StreamSplicer {
    private static final int BUFFER_SIZE = 16 * 1024;

    private StreamSplicer() {}

    /** Starts both up and down threads. */
    public static void splice(Socket socket, IrohStream stream, String label) {

        splice(socket, stream, label, () -> {});
    }

    /**
     * @param onFinished run once both directions have ended and everything is closed. Lets a caller
     *                   track how many connections a tunnel is actually carrying, which is the only
     *                   safe basis for calling that tunnel idle.
     */
    public static void splice(Socket socket, IrohStream stream, String label, Runnable onFinished) {

        // Both directions must finish before either side is torn down
        final AtomicInteger remaining = new AtomicInteger(2);
        final Runnable onDirectionDone = () -> {
            if (remaining.decrementAndGet() == 0) {
                closeQuietly(socket, stream);
                onFinished.run();
            }
        };

        IrohThreads.start(label + "-up", () -> {
            try {
                final InputStream in = socket.getInputStream();
                final byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    stream.write(buffer, 0, read);
                }
                // EOF from the local side
                stream.finishSend();
            } catch (IOException e) {
                Constants.LOG.debug("iroh {} tunnel: local -> remote ended ({})", label, e.toString());
            } finally {
                onDirectionDone.run();
            }
        });

        IrohThreads.start(label + "-down", () -> {
            try {
                final OutputStream out = socket.getOutputStream();
                byte[] chunk;
                while ((chunk = stream.read(BUFFER_SIZE)) != null) {
                    out.write(chunk);
                    out.flush();
                }

                if (!socket.isOutputShutdown()) {
                    socket.shutdownOutput();
                }
            } catch (IOException e) {
                Constants.LOG.debug("iroh {} tunnel: remote -> local ended ({})", label, e.toString());
            } finally {
                onDirectionDone.run();
            }
        });
    }

    private static void closeQuietly(Socket socket, IrohStream stream) {

        try {
            socket.close();
        } catch (IOException e) {
            Constants.LOG.debug("Failed to close tunnel socket", e);
        }
        try {
            stream.close();
        } catch (Exception e) {
            Constants.LOG.debug("Failed to close iroh stream", e);
        }
    }
}
