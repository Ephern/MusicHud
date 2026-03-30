package indi.etern.musichud.platform.plugin.paper.event;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.IEventService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PaperEventService implements IEventService, Listener {
    private static volatile PaperEventService instance;
    private final Logger logger = MusicHud.getLogger(PaperEventService.class);
    private final List<Consumer<ServerPlayer>> commonPlayerQuitListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> serverLifecycleStoppingListeners = new CopyOnWriteArrayList<>();
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
    public void initialize() {
    }

    @Override
    public void registerClientPlayerJoin(Consumer<Player> listener) {
        throw unsupportedClientOperation();
    }

    @Override
    public void registerClientPlayerQuit(Consumer<Player> listener) {
        throw unsupportedClientOperation();
    }

    @Override
    public void registerClientTickPost(Runnable listener) {
        throw unsupportedClientOperation();
    }

    @Override
    public void registerClientLifecycleStopping(Runnable listener) {
        throw unsupportedClientOperation();
    }

    @Override
    public void registerCommonPlayerQuit(Consumer<ServerPlayer> listener) {
        commonPlayerQuitListeners.add(listener);
    }

    @Override
    public void registerServerLifecycleStopping(Runnable listener) {
        serverLifecycleStoppingListeners.add(listener);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ServerPlayer serverPlayer = ((CraftPlayer) event.getPlayer()).getHandle();
        for (Consumer<ServerPlayer> listener : commonPlayerQuitListeners) {
            runSafely(() -> listener.accept(serverPlayer), "common player quit");
        }
    }

    public void fireServerStopping() {
        for (Runnable listener : serverLifecycleStoppingListeners) {
            runSafely(listener, "server stopping");
        }
    }

    private UnsupportedOperationException unsupportedClientOperation() {
        return new UnsupportedOperationException("Client events are not available in the native Paper adapter");
    }

    private void runSafely(Runnable runnable, String phase) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            logger.error("Failed to run Paper event listener during {}", phase, e);
        }
    }
}
