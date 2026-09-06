package indi.etern.musichud.platform.plugin.velocity.event;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import indi.etern.musichud.interfaces.ICommonEventService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.platform.plugin.velocity.network.VelocityPlayerProxy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class VelocityEventService implements ICommonEventService {
    private static volatile VelocityEventService instance;
    private final Set<Consumer<IPlayerClient>> disconnectListeners = new HashSet<>();
    private final Set<Runnable> stoppingListeners = new HashSet<>();
    private boolean registered;

    private VelocityEventService() {
    }

    public static VelocityEventService getInstance() {
        if (instance == null) {
            synchronized (VelocityEventService.class) {
                if (instance == null) {
                    instance = new VelocityEventService();
                }
            }
        }
        return instance;
    }

    public void initialize(ProxyServer proxyServer, Object plugin) {
        if (registered) {
            return;
        }
        proxyServer.getEventManager().register(plugin, this);
        registered = true;
    }

    @Override
    public Unregister registerCommonPlayerQuit(Consumer<IPlayerClient> listener) {
        disconnectListeners.add(listener);
        return () -> {
            disconnectListeners.remove(listener);
        };
    }

    @Override
    public Unregister registerCommonLifecycleStopping(Runnable listener) {
        stoppingListeners.add(listener);
        return () -> {
            stoppingListeners.remove(listener);
        };
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        for (Consumer<IPlayerClient> listener : List.copyOf(disconnectListeners)) {
            listener.accept(VelocityPlayerProxy.ofPlayer(event.getPlayer()));
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        fireServerStopping();
    }

    public void fireServerStopping() {
        for (Runnable listener : List.copyOf(stoppingListeners)) {
            listener.run();
        }
    }
}
