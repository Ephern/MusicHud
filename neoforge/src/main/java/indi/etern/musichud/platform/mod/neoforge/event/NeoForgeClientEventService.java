package indi.etern.musichud.platform.mod.neoforge.event;

import indi.etern.musichud.interfaces.IClientEventService;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.util.function.Consumer;

public class NeoForgeClientEventService implements IClientEventService {
    private static volatile NeoForgeClientEventService instance;
    private Consumer<Player> joinListener;
    private Consumer<Player> quitListener;
    private Runnable tickPostListener;
    private Runnable stoppingListener;

    private NeoForgeClientEventService() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void registerClientPlayerJoin(Consumer<Player> listener) {
        this.joinListener = listener;
    }

    @Override
    public void registerClientPlayerQuit(Consumer<Player> listener) {
        this.quitListener = listener;
    }

    @Override
    public void registerClientTickPost(Runnable listener) {
        this.tickPostListener = listener;
    }

    @Override
    public void registerClientLifecycleStopping(Runnable listener) {
        this.stoppingListener = listener;
    }

    @SubscribeEvent
    public void onClientPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (joinListener != null) {
            joinListener.accept(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onClientPlayerQuit(ClientPlayerNetworkEvent.LoggingOut event) {
        if (quitListener != null) {
            quitListener.accept(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onClientTickPost(ClientTickEvent.Post event) {
        if (tickPostListener != null) {
            tickPostListener.run();
        }
    }

    @SubscribeEvent
    public void onGameShuttingDown(GameShuttingDownEvent event) {
        if (stoppingListener != null) {
            stoppingListener.run();
        }
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