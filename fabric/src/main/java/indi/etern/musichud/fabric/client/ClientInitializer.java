package indi.etern.musichud.fabric.client;

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class ClientInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfigEvents.loading(MusicHud.MOD_ID).register((config) -> {
            if (config.getSpec() == ClientConfigDefinition.configure.getRight()) {
                HudRendererManager hudRendererManager = HudRendererManager.getInstance();
                HudElementRegistry.addFirst(
                        MusicHud.location("main_hud"),
                        hudRendererManager::renderFrame
                );
            }
        });
    }
}
