package indi.etern.musichud.platform.mod.fabric.event;

import indi.etern.musichud.interfaces.IServerEventService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public class FabricServerEventService implements IServerEventService {
    private static volatile FabricServerEventService instance;

    private FabricServerEventService() {}

    @Override
    public void registerCommonPlayerQuit(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            listener.accept(handler.getPlayer());
        });
    }

    @Override
    public void registerServerLifecycleStopping(Runnable listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> listener.run());
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