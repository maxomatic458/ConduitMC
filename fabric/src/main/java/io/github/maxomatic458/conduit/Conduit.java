package io.github.maxomatic458.conduit;

import io.github.maxomatic458.conduit.net.server.DedicatedServerHosting;
import io.github.maxomatic458.conduit.net.server.IrohConfigCommand;
import io.github.maxomatic458.conduit.platform.FabricConfigService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Conduit implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricConfigService.register();

        Constants.LOG.info("Conduit starting on Fabric");
        CommonClass.init();

        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerHosting::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerHosting::onServerStopping);

        // Server-side settings command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                IrohConfigCommand.register(dispatcher));
    }
}
