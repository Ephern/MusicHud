package indi.etern.musichud.platform.plugin.paper.event;

import indi.etern.musichud.interfaces.ICommonEventService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.platform.plugin.paper.network.PaperPlayerProxy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class PaperEventService implements ICommonEventService, Listener {
    private static volatile PaperEventService instance;
    private final Set<Consumer<IPlayerClient>> disconnectListeners = new HashSet<>();
    private final Set<Runnable> stoppingListeners = new HashSet<>();
    private JavaPlugin plugin;

    private PaperEventService() {
    }

    public static PaperEventService getInstance() {
        if (instance == null) {
            synchronized (PaperEventService.class) {
                if (instance == null) {
                    instance = new PaperEventService();
                }
            }
        }
        return instance;
    }

    public void initialize(JavaPlugin plugin) {
        if (this.plugin == plugin) {
            return;
        }
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        disconnectListeners.forEach(d -> d.accept(PaperPlayerProxy.ofPlayer(event.getPlayer())));
    }

    public void fireServerStopping() {
        stoppingListeners.forEach(Runnable::run);
    }
}
