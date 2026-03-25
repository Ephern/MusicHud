package indi.etern.musichud.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import net.fabricmc.api.ModInitializer;

public final class CommonInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        ModConfigEvents.loading(MusicHud.MOD_ID).register(modConfig -> {
            MusicHud.checkConfigAndInit(modConfig.getSpec());
        });
    }
}
