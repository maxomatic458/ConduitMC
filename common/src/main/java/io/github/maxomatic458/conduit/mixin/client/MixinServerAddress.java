package io.github.maxomatic458.conduit.mixin.client;

import io.github.maxomatic458.conduit.irohnet.IrohNet;
import io.github.maxomatic458.conduit.net.IrohConfig;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the multiplayer screens accept a connect ID as a server address.
 */
@Mixin(ServerAddress.class)
public class MixinServerAddress {

    @Inject(method = "isValidAddress", at = @At("HEAD"), cancellable = true)
    private static void conduit$acceptConnectId(String address, CallbackInfoReturnable<Boolean> cir) {

        // isEndpointId is a plain regex and is checked first: this runs on every keystroke in the
        // address box, and ordinary typing must not touch the config or the native library.
        if (IrohNet.isEndpointId(address) && IrohConfig.get().isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
