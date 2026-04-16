package indi.etern.musichud.paper;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.etern.musichud.platform.plugin.paper.event.PaperEventService;
import indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager;
import indi.etern.musichud.server.api.ApiServerManager;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public final class CommonInitializer extends JavaPlugin {
    private PaperEventService eventService;
    private PaperNetworkManager networkManager;

    @Override
    public void onEnable() {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.PAPER));

        saveDefaultConfig();

        eventService = PaperEventService.getInstance();
        eventService.initialize(this);
        networkManager = PaperNetworkManager.getInstance();
        networkManager.initialize(this);

        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.initialize(this);

        try {
            MusicHud.init();
            MusicHud.onConfigLoaded();
        } catch (RuntimeException e) {
            shutdownServices();
            throw e;
        }
    }

    @Override
    public void onDisable() {
        shutdownServices();
    }

    private void shutdownServices() {
        if (eventService != null) {
            eventService.fireServerStopping();
        }
        ApiServerManager.getInstance().stopApiServer();
        if (networkManager != null) {
            networkManager.close();
            networkManager = null;
        }
        eventService = null;
    }
}
