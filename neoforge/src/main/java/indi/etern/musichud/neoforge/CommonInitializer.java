package indi.etern.musichud.neoforge;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MusicHud.MOD_ID)
public final class CommonInitializer {
    private final ServerConfig serverConfig = ServerConfigDefinition.getInstance();
    private final ClientConfig clientConfig = ClientConfigDefinition.getInstance();

    @SuppressWarnings("unused")
    public CommonInitializer(IEventBus eventBus, ModContainer container) {
        eventBus.register(this);

        if (Platform.getEnvironment() == Env.CLIENT) {
            MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.CLIENT, Environment.Platform.NEOFORGE));
            container.registerConfig(ModConfig.Type.COMMON, ServerConfigDefinition.configure.getRight());
            container.registerConfig(ModConfig.Type.CLIENT, ClientConfigDefinition.configure.getRight());
            container.registerExtensionPoint(IConfigScreenFactory.class, new ConfigScreenFactory());
        } else {
            MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.FABRIC));
            container.registerConfig(ModConfig.Type.SERVER, ServerConfigDefinition.configure.getRight());
        }
    }

    public static HudRendererManager hudRendererManager;

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (hudRendererManager != null) {
            hudRendererManager.renderFrame(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    void onConfigEvent(final ModConfigEvent configEvent) {
        ModConfig config = configEvent.getConfig();
        if (config.getSpec() == ServerConfigDefinition.configure.getRight()) {
            serverConfig.setConfigured(true);
        } else if (config.getSpec() == ClientConfigDefinition.configure.getRight()) {
            clientConfig.setConfigured(true);
            hudRendererManager = HudRendererManager.getInstance();
            NeoForge.EVENT_BUS.addListener(CommonInitializer::onRenderGui);
        }
    }

    @SubscribeEvent
    void onConfigReload(final ModConfigEvent.Reloading configEvent) {
        onConfigEvent(configEvent);
    }
    @SubscribeEvent
    void onConfigLoaded(final ModConfigEvent.Loading configEvent) {
        ModConfig config = configEvent.getConfig();
        if (config.getModId().equals(MusicHud.MOD_ID)) {
            onConfigEvent(configEvent);
            boolean inClient = Platform.getEnvironment() == Env.CLIENT;
            if ((inClient && clientConfig.isConfigured() && serverConfig.isConfigured())
                    || (!inClient && serverConfig.isConfigured())) {
                MusicHud.init();
            }
        }
    }
}