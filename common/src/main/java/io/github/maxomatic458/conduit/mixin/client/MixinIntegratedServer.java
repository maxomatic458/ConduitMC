package io.github.maxomatic458.conduit.mixin.client;

import io.github.maxomatic458.conduit.net.IrohConfig;
import io.github.maxomatic458.conduit.net.IrohThreads;
import io.github.maxomatic458.conduit.net.server.IrohAnnouncer;
import io.github.maxomatic458.conduit.net.server.IrohServerHost;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes a singleplayer world over iroh whenever it is opened to LAN.
 */
@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServer {

    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("RETURN"))
    private void conduit$startIrohHost(MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> cir) {

        if (!cir.getReturnValueZ() || !IrohConfig.get().isEnabled() || !IrohConfig.get().shouldHostAutomatically()) {
            return;
        }

        final MinecraftServer server = (MinecraftServer) (Object) this;
        // Binding an endpoint waits for a relay to become usable
        IrohThreads.start("publish", () -> {
            final String connectId = IrohServerHost.start(port);
            server.execute(() -> IrohAnnouncer.announce(server, connectId));
        });
    }

    @Inject(method = "unpublishServer", at = @At("RETURN"))
    private void conduit$stopIrohHost(CallbackInfoReturnable<Boolean> cir) {

        if (cir.getReturnValueZ()) {
            IrohServerHost.stop();
        }
    }
}
