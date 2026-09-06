package indi.etern.musichud.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.plugin.velocity.config.VelocityServerConfigDefinition;
import indi.etern.musichud.platform.plugin.velocity.event.VelocityEventService;
import indi.etern.musichud.platform.plugin.velocity.network.VelocityNetworkManager;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.velocity.templates.BuildConstants;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "music_hud",
        name = "MusicHud",
        version = BuildConstants.VERSION,
        description = "Enjoy music in all situations powered by Netease Cloud Music (NCM) | 基于网易云的全场景音乐插件",
        authors = {"Etern"}
)
@SuppressWarnings("unused")
public final class CommonInitializer {
    private final ProxyServer proxyServer;
    private final Path dataDirectory;
    private VelocityEventService eventService;
    private VelocityNetworkManager networkManager;

    @Inject
    public CommonInitializer(ProxyServer proxyServer, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        MusicHud.setCurrentEnvironment(Environment.of(Environment.Side.SERVER, Environment.Platform.VELOCITY));

        VelocityServerConfigDefinition serverConfig = VelocityServerConfigDefinition.getInstance();
        serverConfig.initialize(dataDirectory);

        eventService = VelocityEventService.getInstance();
        eventService.initialize(proxyServer, this);
        networkManager = VelocityNetworkManager.getInstance();
        networkManager.initialize(proxyServer);
        proxyServer.getEventManager().register(this, networkManager);

        try {
            MusicHud.init();
            MusicHud.onConfigLoaded();
        } catch (RuntimeException e) {
            shutdownServices();
            throw e;
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
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
