package indi.etern.musichud.fabric.client;

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfigEvents.loading(MusicHud.MOD_ID).register((config) -> {
            if (config.getSpec() == ClientConfigDefinition.configure.getRight()) {
                HudRendererManager hudRendererManager = HudRendererManager.getInstance();
                HudElementRegistry.addFirst(
                        Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "main_hud"),
                        hudRendererManager::renderFrame
                );
            }
        });
    }
}
