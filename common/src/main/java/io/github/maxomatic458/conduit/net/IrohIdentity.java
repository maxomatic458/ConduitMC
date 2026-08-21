package io.github.maxomatic458.conduit.net;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * This client's iroh identity
 */
public final class IrohIdentity {

    private static final Object LOCK = new Object();

    private static volatile String secretKeyHex;

    private IrohIdentity() {}

    private static Path path() {

        return Services.PLATFORM.getConfigDirectory().resolve(Constants.MOD_ID + "-identity.txt");
    }

    private static String load() {

        String key = secretKeyHex;
        if (key != null) {
            return key;
        }
        synchronized (LOCK) {
            if (secretKeyHex != null) {
                return secretKeyHex;
            }
            final Path path = path();
            if (Files.exists(path)) {
                try {
                    key = Files.readString(path, StandardCharsets.UTF_8).trim();
                } catch (IOException e) {
                    Constants.LOG.error("Could not read {}. A new identity will be generated and the connect ID will change.", path, e);
                }
            }
            if (key == null || key.isBlank()) {
                key = IrohNet.isSupported() ? HexFormat.of().formatHex(IrohNet.generateSecretKey()) : "";
                if (!key.isEmpty()) {
                    write(key);
                }
            }
            secretKeyHex = key;
            return key;
        }
    }

    private static void write(String key) {

        final Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, key + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.warn("Could not write {}; the connect ID will not survive a restart.", path, e);
        }
    }

    /**
     * The 32-byte secret key
     *
     * @throws IOException if none is available, which happens when iroh is unsupported here
     */
    public static byte[] secretKey() throws IOException {

        final String key = load();
        if (key == null || key.isBlank()) {
            throw new IOException("no iroh identity is configured");
        }
        try {
            final byte[] bytes = HexFormat.of().parseHex(key);
            if (bytes.length != 32) {
                throw new IOException("iroh secret key must be 32 bytes, got " + bytes.length);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IOException("iroh secret key in " + path() + " is not valid hex", e);
        }
    }

    /** The permanent connect ID */
    public static String connectId() {

        try {
            return IrohNet.endpointIdOf(secretKey());
        } catch (IOException e) {
            Constants.LOG.debug("Could not derive the connect ID", e);
            return null;
        }
    }

    /** Replaces the identity with a freshly generated one. */
    public static String regenerate() throws IOException {

        if (!IrohNet.isSupported()) {
            throw new IOException("iroh is unavailable: " + IrohNet.unsupportedReason());
        }
        synchronized (LOCK) {
            final String key = HexFormat.of().formatHex(IrohNet.generateSecretKey());
            write(key);
            secretKeyHex = key;
        }
        return IrohNet.endpointIdOf(secretKey());
    }
}
