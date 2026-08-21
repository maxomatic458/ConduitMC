package io.github.maxomatic458.conduit;

import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.net.IrohIdentity;
import io.github.maxomatic458.conduit.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/// Cross-loader common code
public class CommonClass {

    public static void init() {
        logIrohStatus();
    }

    // Reports whether the iroh native library loaded, and under which identity.
    private static void logIrohStatus() {

        if (!IrohNet.isSupported()) {
            Constants.LOG.warn("iroh is unavailable, connect IDs will not work: {}", IrohNet.unsupportedReason());
            return;
        }

        final String connectId = IrohIdentity.connectId();
        if (connectId == null) {
            Constants.LOG.warn("iroh loaded, but no identity could be derived; check config/{}.json", Constants.MOD_ID);
        } else {
            Constants.LOG.info("iroh ready. This installation's connect ID is {}", connectId);
        }
    }
}