package indi.etern.musichud.fabric.client;

import indi.etern.musichud.client.ui.ModernUiSupport;
import net.fabricmc.api.ClientModInitializer;

public final class ClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (ModernUiSupport.available()) {
            ClientUiBootstrap.start();
        }
    }
}
