package io.github.maxomatic458.conduit.client;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.net.client.IrohInfoReport;
import io.github.maxomatic458.conduit.platform.FabricConfigService;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

public class ConduitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // client side info command
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal(Constants.MOD_ID + "-info").executes(context -> {
                    context.getSource().sendFeedback(IrohInfoReport.build());
                    return 1;
                })));

        // Adds a button to the vanilla Options screen for mod settings
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen) {
                Screens.getWidgets(screen).add(Button.builder(
                                Component.translatable(Constants.MOD_ID + ".config.title"),
                                button -> openConfigScreen(screen))
                        .bounds(6, screen.height - 26, 120, 20)
                        .build());
            }
        });
    }

    /** Opens the settings screen Cloth Config generates from the config class. */
    public static void openConfigScreen(Screen parent) {

        FabricConfigService.register();
        Minecraft.getInstance().gui.setScreen(
                AutoConfigClient.getConfigScreen(FabricConfigService.IrohModConfig.class, parent).get());
    }
}
