package indi.etern.musichud.platform.mod.architectury.event;

import dev.architectury.event.EventHandler;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import indi.etern.musichud.interfaces.IEventService;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public class ModEventService implements IEventService {
    private ModEventService() {

    }
    public void initialize() {
        EventHandler.init();
    }

    @Override
    public void registerClientPlayerJoin(Consumer<LocalPlayer> listener) {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(listener::accept);
    }

    @Override
    public void registerClientPlayerQuit(Consumer<LocalPlayer> listener) {
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(listener::accept);
    }

    @Override
    public void registerClientTickPost(Runnable listener) {
        ClientTickEvent.CLIENT_POST.register(minecraft -> listener.run());
    }

    @Override
    public void registerClientLifecycleStopping(Runnable listener) {
        ClientLifecycleEvent.CLIENT_STOPPING.register(minecraft -> listener.run());
    }

    @Override
    public void registerCommonPlayerQuit(Consumer<ServerPlayer> listener) {
        PlayerEvent.PLAYER_QUIT.register(listener::accept);
    }

    @Override
    public void registerServerLifecycleStopping(Runnable listener) {
        LifecycleEvent.SERVER_STOPPING.register(minecraft -> listener.run());
    }

    private static volatile ModEventService instance;
    public static ModEventService getInstance() {
        if (instance == null) {
            synchronized (ModEventService.class) {
                if (instance == null) {
                    instance = new ModEventService();
                }
            }
        }
        return instance;
    }
}
