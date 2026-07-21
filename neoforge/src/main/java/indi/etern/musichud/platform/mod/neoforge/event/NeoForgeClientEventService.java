package indi.etern.musichud.platform.mod.neoforge.event;

import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class NeoForgeClientEventService implements IClientEventService {
    private String serverIp;
    private static volatile NeoForgeClientEventService instance;
    private final Set<Consumer<Player>> joinListeners = new HashSet<>();
    private final Set<Consumer<Player>> quitListeners = new HashSet<>();
    private final Set<Runnable> tickPostListeners = new HashSet<>();

    private NeoForgeClientEventService() {
        NeoForge.EVENT_BUS.register(this);
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

    @SubscribeEvent
    public void onClientPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ServerData currentServer = Minecraft.getInstance().getCurrentServer();
        if (currentServer == null || !currentServer.ip.equals(serverIp)) {
            serverIp = currentServer == null ? null : currentServer.ip;
            joinListeners.forEach(l -> l.accept(event.getPlayer()));
        }
    }

    @SubscribeEvent
    public void onClientPlayerQuit(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            serverIp = null;
            quitListeners.forEach(q -> q.accept(event.getPlayer()));
        }
    }

    @SubscribeEvent
    public void onClientTickPost(ClientTickEvent.Post event) {
        tickPostListeners.forEach(Runnable::run);
    }

    public static NeoForgeClientEventService getInstance() {
        if (instance == null) {
            synchronized (NeoForgeClientEventService.class) {
                if (instance == null) {
                    instance = new NeoForgeClientEventService();
                }
            }
        }
        return instance;
    }
}