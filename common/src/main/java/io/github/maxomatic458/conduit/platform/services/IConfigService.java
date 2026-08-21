package io.github.maxomatic458.conduit.platform.services;

import java.util.List;

/**
 * The user-facing settings, backed by whatever config system the loader provides.
 *
 * NeoForge stores them in a {@code ModConfigSpec} (TOML) and Fabric in a Cloth Config class
 */
public interface IConfigService {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean shouldHostAutomatically();

    void setHostAutomatically(boolean hostAutomatically);

    int connectTimeoutSeconds();

    void setConnectTimeoutSeconds(int seconds);

    int tunnelIdleTimeoutSeconds();

    void setTunnelIdleTimeoutSeconds(int seconds);

    List<String> relayUrls();

    void setRelayUrls(List<String> urls);

    String alpn();

    /** Persists any changes made through the setters. */
    void save();
}
