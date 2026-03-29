package indi.etern.musichud.paper;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.PlatformServiceRegistry;
import indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition;
import indi.etern.musichud.platform.plugin.paper.event.PaperEventService;
import indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager;
import indi.etern.musichud.server.api.ServerApiMeta;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommonInitializer extends JavaPlugin {
    private final Logger logger = MusicHud.getLogger(CommonInitializer.class);
    private PaperEventService eventService;
    private PaperNetworkManager networkManager;

    @Override
    public void onEnable() {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.PAPER));

        saveDefaultConfig();

        eventService = new PaperEventService(this);
        networkManager = new PaperNetworkManager(this);

        PlatformServiceRegistry.setEventService(eventService);
        PlatformServiceRegistry.setNetworkRegister(networkManager);
        PlatformServiceRegistry.setServerNetworkService(networkManager);

        ServerConfigDefinition serverConfig = ServerConfigDefinition.getInstance();
        serverConfig.initialize(this);
        PlatformServiceRegistry.setServerConfig(serverConfig);

        try {
            MusicHud.init();
        } catch (RuntimeException e) {
            shutdownServices();
            throw e;
        }

        logger.info("MusicHud native Paper adapter enabled");
    }

    @Override
    public void onDisable() {
        shutdownServices();
        logger.info("MusicHud native Paper adapter disabled");
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
        PlatformServiceRegistry.clear();
    }
}
