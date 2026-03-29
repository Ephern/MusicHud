package indi.etern.musichud.paper;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.IEventService;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.etern.musichud.platform.plugin.paper.event.PaperEventService;
import indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager;
import indi.etern.musichud.server.api.ServerApiMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommonInitializer extends JavaPlugin {
    private PaperEventService eventService;
    private PaperNetworkManager networkManager;

    @Override
    public void onEnable() {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.PAPER));

        saveDefaultConfig();

        eventService = new PaperEventService(this);
        networkManager = new PaperNetworkManager(this);

        IEventService.setInstance(eventService);
        INetworkRegister.setInstance(networkManager);
        IServerNetworkService.setInstance(networkManager);

        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.initialize(this);
        ServerConfig.setInstance(serverConfig);

        try {
            MusicHud.init();
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
        ServerApiMeta.Register.stopApiServer();
        if (networkManager != null) {
            networkManager.close();
            networkManager = null;
        }
        eventService = null;
        IEventService.setInstance(null);
        INetworkRegister.setInstance(null);
        IServerNetworkService.setInstance(null);
        ServerConfig.setInstance(null);
    }
}
