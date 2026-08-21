package io.github.maxomatic458.conduit.net;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohEndpointSettings;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.platform.Services;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Mod configuration
 */
public final class IrohConfig {

    private static final IrohConfig INSTANCE = new IrohConfig();

    private IrohConfig() {}

    public static IrohConfig get() {

        return INSTANCE;
    }

    public boolean isEnabled() {

        return Services.CONFIG.isEnabled() && IrohNet.isSupported();
    }

    /*If the mod is enabled in the config */
    public boolean isEnabledSetting() {

        return Services.CONFIG.isEnabled();
    }

    public void setEnabled(boolean enabled) {

        Services.CONFIG.setEnabled(enabled);
    }

    public boolean shouldHostAutomatically() {

        return Services.CONFIG.shouldHostAutomatically();
    }

    public void setHostAutomatically(boolean hostAutomatically) {

        Services.CONFIG.setHostAutomatically(hostAutomatically);
    }

    public int connectTimeoutSeconds() {

        return Services.CONFIG.connectTimeoutSeconds();
    }

    public void setConnectTimeoutSeconds(int seconds) {

        Services.CONFIG.setConnectTimeoutSeconds(seconds);
    }

    public int tunnelIdleTimeoutSeconds() {

        return Services.CONFIG.tunnelIdleTimeoutSeconds();
    }

    public void setTunnelIdleTimeoutSeconds(int seconds) {

        Services.CONFIG.setTunnelIdleTimeoutSeconds(seconds);
    }

    public List<String> relayUrls() {

        return Services.CONFIG.relayUrls();
    }

    public void setRelayUrls(List<String> urls) {

        Services.CONFIG.setRelayUrls(urls);
    }

    public String alpn() {

        final String alpn = Services.CONFIG.alpn();
        return alpn == null || alpn.isBlank() ? IrohNet.DEFAULT_ALPN : alpn;
    }

    public void save() {

        Services.CONFIG.save();
    }

    public long connectTimeoutMillis() {

        return Math.max(1, connectTimeoutSeconds()) * 1000L;
    }

    public long tunnelIdleTimeoutMillis() {

        return Math.max(30, tunnelIdleTimeoutSeconds()) * 1000L;
    }

    /* SecretKey / IrohIdentity is stored in config/conduit-identity.txt */
    public byte[] secretKey() throws IOException {

        return IrohIdentity.secretKey();
    }

    public String connectId() {

        return IrohIdentity.connectId();
    }

    public String regenerateIdentity() throws IOException {

        return IrohIdentity.regenerate();
    }

    public static List<String> parseRelayUrls(String text) {

        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[,\\s]+"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    /**
     * Transport settings for a new endpoint.
     */
    public IrohEndpointSettings endpointSettings() {

        List<String> urls = relayUrls();
        final String problem = IrohNet.validateRelayUrls(urls);
        if (problem != null) {
            Constants.LOG.warn("Ignoring relay URLs {}: {}. Falling back to the default relays.", urls, problem);
            urls = List.of();
        }
        return new IrohEndpointSettings(alpn(), urls);
    }
}
