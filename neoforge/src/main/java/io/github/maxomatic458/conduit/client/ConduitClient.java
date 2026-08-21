package io.github.maxomatic458.conduit.client;

import io.github.maxomatic458.conduit.Constants;
import io.github.maxomatic458.conduit.net.client.IrohInfoReport;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point.
 */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class ConduitClient {

    public ConduitClient(ModContainer container) {

        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /** Opens the generated settings screen. */
    public static void openConfigScreen(net.minecraft.client.gui.screens.Screen parent) {

        ModList.get().getModContainerById(Constants.MOD_ID).ifPresent(container ->
                IConfigScreenFactory.getForMod(container.getModInfo())
                        .map(factory -> factory.createScreen(container, parent))
                        .ifPresent(screen -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen)));
    }

    /**
     * Adds a button to the vanilla Options screen.
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {

        if (!(event.getScreen() instanceof OptionsScreen screen)) {
            return;
        }
        event.addListener(Button.builder(
                        Component.translatable(Constants.MOD_ID + ".config.title"),
                        button -> openConfigScreen(screen))
                .bounds(6, screen.height - 26, 120, 20)
                .build());
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {

        // A client-side command: it never reaches the server, so it works on vanilla servers too.
        event.getDispatcher().register(Commands.literal(Constants.MOD_ID + "-info").executes(context -> {
            context.getSource().sendSuccess(IrohInfoReport::build, false);
            return 1;
        }));
    }
}
