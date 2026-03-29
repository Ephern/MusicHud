package indi.etern.musichud.fabric;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.IConfigSpec;

public final class CommonInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        boolean inClient = Platform.getEnvironment() == Env.CLIENT;
        MusicHud.setCurrentEnvironment(Environment.of(inClient ? Environment.Side.CLIENT : Environment.Side.SERVER, Environment.Platform.FABRIC));
        ServerConfig serverConfig = ServerConfig.getInstance();
        ClientConfig clientConfig = ClientConfig.getInstance();
        ModConfigEvents.loading(MusicHud.MOD_ID).register(modConfig -> {
            IConfigSpec modConfigSpec = modConfig.getSpec();
            if (modConfigSpec.equals(ServerConfigDefinition.configure.getRight())) {
                serverConfig.setConfigured(true);
            } else if (modConfigSpec.equals(ClientConfigDefinition.configure.getRight())) {
                clientConfig.setConfigured(true);
            }
            if ((inClient && clientConfig.isConfigured() && serverConfig.isConfigured())
                || (!inClient && serverConfig.isConfigured())) {
                MusicHud.init();
            }
        });
    }
}
