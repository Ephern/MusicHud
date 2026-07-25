package indi.etern.musichud.fabric.config;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.neoforged.fml.config.ModConfig;

public class ConfigRegister implements DedicatedServerModInitializer, ClientModInitializer {
    @Override
    public void onInitializeServer() {
        NeoForgeConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.SERVER, ServerConfigDefinition.configure.getRight());
    }

    @Override
    public void onInitializeClient() {
        NeoForgeConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.CLIENT, ClientConfigDefinition.configure.getRight());
        NeoForgeConfigRegistry.INSTANCE.register(MusicHud.MOD_ID, ModConfig.Type.COMMON, ServerConfigDefinition.configure.getRight());
    }
}