package indi.etern.musichud.fabric.client;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NeoForgeModConfigEvents.loading(MusicHud.MOD_ID).register((config) -> {
            if (config.getSpec() == ClientConfigDefinition.configure.getRight()) {
                HudRendererManager hudRendererManager = HudRendererManager.getInstance();
                HudRenderCallback.EVENT.register(
                        MusicHud.location("main_hud"),
                        hudRendererManager::renderFrame
                );
            }
        });
    }
}
