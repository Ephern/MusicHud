package indi.etern.musichud.platform.mod.architectury.event;

import dev.architectury.event.EventHandler;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import indi.etern.musichud.interfaces.IServerEventService;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ModServerEventService implements IServerEventService {
    private ModServerEventService() {
        EventHandler.init();
    }

    @Override
    public void registerCommonPlayerQuit(Consumer<ServerPlayer> listener) {
        PlayerEvent.PLAYER_QUIT.register(listener::accept);
    }

    @Override
    public void registerServerLifecycleStopping(Runnable listener) {
        LifecycleEvent.SERVER_STOPPING.register(minecraft -> listener.run());
    }

    private static volatile ModServerEventService instance;
    public static ModServerEventService getInstance() {
        if (instance == null) {
            synchronized (ModServerEventService.class) {
                if (instance == null) {
                    instance = new ModServerEventService();
                }
            }
        }
        return instance;
    }
}
