package indi.etern.musichud.platform.mod.fabric.event;

import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.vanillaUtils.VanillaPlayerProxy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class FabricServerEventService implements IServerEventService {
    private static volatile FabricServerEventService instance;
    private final Set<Consumer<IPlayerClient>> disconnectListeners = new HashSet<>();
    private final Set<Runnable> stoppingListeners = new HashSet<>();

    private FabricServerEventService() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            disconnectListeners.forEach(d -> d.accept(VanillaPlayerProxy.ofPlayer(handler.getPlayer())));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stoppingListeners.forEach(Runnable::run));
    }

    @Override
    public Unregister registerCommonPlayerQuit(Consumer<IPlayerClient> listener) {
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

    public static FabricServerEventService getInstance() {
        if (instance == null) {
            synchronized (FabricServerEventService.class) {
                if (instance == null) {
                    instance = new FabricServerEventService();
                }
            }
        }
        return instance;
    }
}