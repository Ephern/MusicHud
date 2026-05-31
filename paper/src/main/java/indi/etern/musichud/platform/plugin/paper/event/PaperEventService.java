package indi.etern.musichud.platform.plugin.paper.event;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class PaperEventService implements IServerEventService, Listener {
    private static volatile PaperEventService instance;
    private final Logger logger = MusicHud.getLogger(PaperEventService.class);
    private final Set<Consumer<Player>> disconnectListeners = new HashSet<>();
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
    public Unregister registerCommonPlayerQuit(Consumer<Player> listener) {
        disconnectListeners.add(listener);
        return () -> {
            disconnectListeners.remove(listener);
        };
    }

    @Override
    public Unregister registerServerLifecycleStopping(Runnable listener) {
        stoppingListeners.add(listener);
        return () -> {
            stoppingListeners.remove(listener);
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = ((CraftPlayer) event.getPlayer()).getHandle();
        disconnectListeners.forEach(d -> d.accept(player));
    }

    public void fireServerStopping() {
        stoppingListeners.forEach(Runnable::run);
    }
}
