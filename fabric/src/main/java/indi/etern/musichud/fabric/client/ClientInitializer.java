package indi.etern.musichud.fabric.client;

import indi.etern.musichud.client.ui.ModernUiSupport;
import indi.etern.musichud.fabric.render.VulkanModBackendRegistrar;
import net.fabricmc.api.ClientModInitializer;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VulkanModBackendRegistrar.register();
        if (ModernUiSupport.available()) {
            ClientUiBootstrap.start();
        }
    }
}
