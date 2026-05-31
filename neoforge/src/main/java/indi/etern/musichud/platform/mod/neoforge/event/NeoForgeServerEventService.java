package indi.etern.musichud.platform.mod.neoforge.event;

import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class NeoForgeServerEventService implements IServerEventService {
    private static volatile NeoForgeServerEventService instance;
    private final Set<Consumer<Player>> disconnectListeners = new HashSet<>();
    private final Set<Runnable> stoppingListeners = new HashSet<>();

    private NeoForgeServerEventService() {
        NeoForge.EVENT_BUS.register(this);
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

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            disconnectListeners.forEach(d -> d.accept(player));
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stoppingListeners.forEach(Runnable::run);
    }

    public static NeoForgeServerEventService getInstance() {
        if (instance == null) {
            synchronized (NeoForgeServerEventService.class) {
                if (instance == null) {
                    instance = new NeoForgeServerEventService();
                }
            }
        }
        return instance;
    }
}