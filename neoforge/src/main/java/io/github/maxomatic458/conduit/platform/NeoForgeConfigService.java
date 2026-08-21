package io.github.maxomatic458.conduit.platform;

import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.platform.services.IConfigService;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * NeoForge's native config: a {@link ModConfigSpec} written to TOML.
 */
public class NeoForgeConfigService implements IConfigService {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue HOST_AUTOMATICALLY;
    private static final ModConfigSpec.IntValue CONNECT_TIMEOUT;
    private static final ModConfigSpec.IntValue IDLE_TIMEOUT;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> RELAY_URLS;
    private static final ModConfigSpec.ConfigValue<String> ALPN;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        ENABLED = builder
                .comment("Allow joining servers by connect ID and hosting over iroh.",
                        "Normal IP addresses are unaffected either way.")
                .translation("conduit.config.enabled")
                .define("enabled", true);

        HOST_AUTOMATICALLY = builder
                .comment("Start an iroh endpoint when a world is opened to LAN, or when a dedicated server starts.")
                .translation("conduit.config.host_automatically")
                .define("hostAutomatically", true);

        CONNECT_TIMEOUT = builder
                .comment("How long to wait for a host to answer before giving up on a join.")
                .translation("conduit.config.connect_timeout")
                .defineInRange("connectTimeoutSeconds", 30, 5, 300);

        IDLE_TIMEOUT = builder
                .comment("How long an unused tunnel is kept alive after you stop using a server.")
                .translation("conduit.config.idle_timeout")
                .defineInRange("tunnelIdleTimeoutSeconds", 300, 30, 86400);

        RELAY_URLS = builder
                .comment("Relay servers used for discovery and as a fallback when a direct connection is not possible.",
                        "These are n0's public relays, listed explicitly so you can see and replace them.",
                        "Clearing the list falls back to the same defaults.")
                .translation("conduit.config.relay_urls")
                // Validated per entry so a typo is rejected at load time rather than at bind time.
                .defineList("relayUrls", IrohNet.DEFAULT_RELAY_URLS,
                        () -> "https://relay.example.com",
                        entry -> entry instanceof String url && IrohNet.validateRelayUrls(List.of(url)) == null);

        ALPN = builder
                .comment("Protocol identifier. Both ends must agree; only change it if you know why.")
                .translation("conduit.config.alpn")
                .define("alpn", IrohNet.DEFAULT_ALPN);

        SPEC = builder.build();
    }

    @Override
    public boolean isEnabled() {

        return ENABLED.get();
    }

    @Override
    public void setEnabled(boolean enabled) {

        ENABLED.set(enabled);
    }

    @Override
    public boolean shouldHostAutomatically() {

        return HOST_AUTOMATICALLY.get();
    }

    @Override
    public void setHostAutomatically(boolean hostAutomatically) {

        HOST_AUTOMATICALLY.set(hostAutomatically);
    }

    @Override
    public int connectTimeoutSeconds() {

        return CONNECT_TIMEOUT.get();
    }

    @Override
    public void setConnectTimeoutSeconds(int seconds) {

        CONNECT_TIMEOUT.set(seconds);
    }

    @Override
    public int tunnelIdleTimeoutSeconds() {

        return IDLE_TIMEOUT.get();
    }

    @Override
    public void setTunnelIdleTimeoutSeconds(int seconds) {

        IDLE_TIMEOUT.set(seconds);
    }

    @Override
    public List<String> relayUrls() {

        return List.copyOf(RELAY_URLS.get());
    }

    @Override
    public void setRelayUrls(List<String> urls) {

        RELAY_URLS.set(List.copyOf(urls));
    }

    @Override
    public String alpn() {

        return ALPN.get();
    }

    @Override
    public void save() {
    }
}
