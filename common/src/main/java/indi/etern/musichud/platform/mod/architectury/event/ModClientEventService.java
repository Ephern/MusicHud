package indi.etern.musichud.platform.mod.architectury.event;

import dev.architectury.event.EventHandler;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import indi.etern.musichud.interfaces.IClientEventService;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ModClientEventService implements IClientEventService {
    private ModClientEventService() {
        EventHandler.init();
    }

    @Override
    public void registerClientPlayerJoin(Consumer<Player> listener) {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(listener::accept);
    }

    @Override
    public void registerClientPlayerQuit(Consumer<Player> listener) {
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

    private static volatile ModClientEventService instance;
    public static ModClientEventService getInstance() {
        if (instance == null) {
            synchronized (ModClientEventService.class) {
                if (instance == null) {
                    instance = new ModClientEventService();
                }
            }
        }
        return instance;
    }
}
