package indi.etern.musichud.platform.mod.neoforge.event;

import indi.etern.musichud.interfaces.IServerEventService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Consumer;

public class NeoForgeServerEventService implements IServerEventService {
    private static volatile NeoForgeServerEventService instance;
    private Consumer<ServerPlayer> playerQuitListener;
    private Runnable serverStoppingListener;

    private NeoForgeServerEventService() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void registerCommonPlayerQuit(Consumer<ServerPlayer> listener) {
        this.playerQuitListener = listener;
    }

    @Override
    public void registerServerLifecycleStopping(Runnable listener) {
        this.serverStoppingListener = listener;
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (playerQuitListener != null && event.getEntity() instanceof ServerPlayer serverPlayer) {
            playerQuitListener.accept(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (serverStoppingListener != null) {
            serverStoppingListener.run();
        }
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