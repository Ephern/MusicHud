package indi.etern.musichud.platform.mod.fabric.event;

import indi.etern.musichud.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.Unregister;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class FabricClientEventService implements IClientEventService {
    private String serverIp = null;
    private static volatile FabricClientEventService instance;
    private final Set<Consumer<Player>> joinListeners = new HashSet<>();
    private final Set<Consumer<Player>> quitListeners = new HashSet<>();
    private final Set<Runnable> tickPostListeners = new HashSet<>();
    private final Set<Runnable> stoppingListeners = new HashSet<>();

    private FabricClientEventService() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData currentServer = client.getCurrentServer();
            if (currentServer == null || !currentServer.ip.equals(serverIp)) {
                serverIp = currentServer == null ? null : currentServer.ip;
                joinListeners.forEach(l -> l.accept(client.player));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serverIp = null;
            if (client.player != null) {
                quitListeners.forEach(q -> q.accept(client.player));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickPostListeners.forEach(Runnable::run));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stoppingListeners.forEach(Runnable::run));
    }

    public static FabricClientEventService getInstance() {
        if (instance == null) {
            synchronized (FabricClientEventService.class) {
                if (instance == null) {
                    instance = new FabricClientEventService();
                }
            }
        }
        return instance;
    }

    @Override
    public Unregister registerClientPlayerJoin(Consumer<Player> listener) {
        joinListeners.add(listener);
        return () -> joinListeners.remove(listener);
    }

    @Override
    public Unregister registerClientPlayerQuit(Consumer<Player> listener) {
        quitListeners.add(listener);
        return () -> quitListeners.remove(listener);
    }

    @Override
    public Unregister registerClientTickPost(Runnable listener) {
        tickPostListeners.add(listener);
        return () -> tickPostListeners.remove(listener);
    }

    @Override
    public Unregister registerClientLifecycleStopping(Runnable listener) {
        stoppingListeners.add(listener);
        return () -> stoppingListeners.remove(listener);
    }
}