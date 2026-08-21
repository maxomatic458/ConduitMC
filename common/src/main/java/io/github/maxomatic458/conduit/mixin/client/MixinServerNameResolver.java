package io.github.maxomatic458.conduit.mixin.client;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.client.IrohClientTunnels;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Optional;

/**
 * Turns a connect ID typed into the server address field into a local tunnel endpoint.
 */
@Mixin(ServerNameResolver.class)
public class MixinServerNameResolver {

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void conduit$resolveIrohAddress(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {

        final String host = address.getHost();
        if (!IrohNet.isEndpointId(host) || !IrohConfig.get().isEnabled()) {
            return;
        }

        try {
            // Returns as soon as a loopback port is bound. 
            // the iroh dial happens on first use
            cir.setReturnValue(Optional.of(ResolvedServerAddress.from(IrohClientTunnels.addressFor(host))));
        } catch (IOException e) {
            Constants.LOG.error("Could not open an iroh tunnel for {}", host, e);
            cir.setReturnValue(Optional.empty());
        }
    }
}
