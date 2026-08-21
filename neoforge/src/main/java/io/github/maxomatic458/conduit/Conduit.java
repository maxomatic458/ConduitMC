package io.github.maxomatic458.conduit;

import io.github.maxomatic458.conduit.net.server.DedicatedServerHosting;
import io.github.maxomatic458.conduit.net.server.IrohConfigCommand;
import io.github.maxomatic458.conduit.platform.NeoForgeConfigService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(Constants.MOD_ID)
public class Conduit {

    public Conduit(IEventBus eventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfigService.SPEC);

        // Use NeoForge to bootstrap the Common mod.
        Constants.LOG.info("Conduit starting on NeoForge");
        CommonClass.init();

    }

    /** Dedicated server hosting rides on the game bus rather than a mixin. */
    @EventBusSubscriber(modid = Constants.MOD_ID)
    public static class ServerEvents {

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {

            DedicatedServerHosting.onServerStarted(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {

            DedicatedServerHosting.onServerStopping(event.getServer());
        }

        /** Server-side settings command, mirroring the client's config screen. */
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {

            IrohConfigCommand.register(event.getDispatcher());
        }
    }
}
