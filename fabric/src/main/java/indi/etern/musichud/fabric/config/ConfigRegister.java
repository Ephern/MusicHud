package indi.etern.musichud.fabric.config;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.neoforged.fml.config.ModConfig;

public class ConfigRegister implements DedicatedServerModInitializer, ClientModInitializer {
    @Override
    public void onInitializeServer() {
        ConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.SERVER, ServerConfigDefinition.configure.getRight());
    }

    @Override
    public void onInitializeClient() {
        ConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.CLIENT, ClientConfigDefinition.configure.getRight());
        ConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.COMMON, ServerConfigDefinition.configure.getRight());
    }
}