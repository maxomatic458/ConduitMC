package io.github.maxomatic458.conduit.platform;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.platform.services.IConfigService;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric's config, backed by Cloth Config's AutoConfig.
 */
public class FabricConfigService implements IConfigService {

    private static volatile IrohModConfig config;

    /**
     * Registers the config with AutoConfig. Must run once, early, before any value is read.
     *
     * <p>Idempotent, because both the main and client entry points may reach it.
     */
    public static synchronized void register() {

        if (config == null) {
            AutoConfig.register(IrohModConfig.class, GsonConfigSerializer::new);
            config = AutoConfig.getConfigHolder(IrohModConfig.class).getConfig();
        }
    }

    private static IrohModConfig config() {

        if (config == null) {
            register();
        }
        return config;
    }

    @Override
    public boolean isEnabled() {

        return config().enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {

        config().enabled = enabled;
    }

    @Override
    public boolean shouldHostAutomatically() {

        return config().hostAutomatically;
    }

    @Override
    public void setHostAutomatically(boolean hostAutomatically) {

        config().hostAutomatically = hostAutomatically;
    }

    @Override
    public int connectTimeoutSeconds() {

        return config().connectTimeoutSeconds;
    }

    @Override
    public void setConnectTimeoutSeconds(int seconds) {

        config().connectTimeoutSeconds = seconds;
    }

    @Override
    public int tunnelIdleTimeoutSeconds() {

        return config().tunnelIdleTimeoutSeconds;
    }

    @Override
    public void setTunnelIdleTimeoutSeconds(int seconds) {

        config().tunnelIdleTimeoutSeconds = seconds;
    }

    @Override
    public List<String> relayUrls() {

        final List<String> urls = config().relayUrls;
        return urls == null ? List.of() : List.copyOf(urls);
    }

    @Override
    public void setRelayUrls(List<String> urls) {

        config().relayUrls = new ArrayList<>(urls);
    }

    @Override
    public String alpn() {

        return config().alpn;
    }

    @Override
    public void save() {

        try {
            AutoConfig.getConfigHolder(IrohModConfig.class).save();
        } catch (RuntimeException e) {
            Constants.LOG.warn("Could not save the iroh config", e);
        }
    }

    /**
     * The config model. Field names become both the JSON keys and the translation keys
     * ({@code text.autoconfig.conduit.option.<field>}).
     */
    @Config(name = Constants.MOD_ID)
    public static class IrohModConfig implements ConfigData {

        public boolean enabled = true;

        public boolean hostAutomatically = true;

        @ConfigEntry.BoundedDiscrete(min = 5, max = 300)
        public int connectTimeoutSeconds = 30;

        @ConfigEntry.BoundedDiscrete(min = 30, max = 3600)
        public int tunnelIdleTimeoutSeconds = 300;

        /**
         * iroh n0's public relays
         */
        public List<String> relayUrls = new ArrayList<>(IrohNet.DEFAULT_RELAY_URLS);

        public String alpn = IrohNet.DEFAULT_ALPN;

        @Override
        public void validatePostLoad() {
            if (alpn == null || alpn.isBlank()) {
                alpn = IrohNet.DEFAULT_ALPN;
            }
            
            if (relayUrls == null) {
                relayUrls = new ArrayList<>();
            }
            connectTimeoutSeconds = Math.clamp(connectTimeoutSeconds, 5, 300);
            tunnelIdleTimeoutSeconds = Math.clamp(tunnelIdleTimeoutSeconds, 30, 3600);
        }
    }
}
