package indi.etern.musichud.platform.mod.fabric.event;

import indi.etern.musichud.interfaces.IClientEventService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public class FabricClientEventService implements IClientEventService {
    private static volatile FabricClientEventService instance;

    private FabricClientEventService() {}

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
    public void registerClientPlayerJoin(Consumer<Player> listener) {
        // CLIENT_PLAYER_JOIN 在 Fabric 中对应 ClientPlayConnectionEvents.JOIN
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.accept(client.player));
    }

    @Override
    public void registerClientPlayerQuit(Consumer<Player> listener) {
        // CLIENT_PLAYER_QUIT 对应 ClientPlayConnectionEvents.DISCONNECT
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.player != null) {
                listener.accept(client.player);
            }
        });
    }

    @Override
    public void registerClientTickPost(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void registerClientLifecycleStopping(Runnable listener) {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> listener.run());
    }
}