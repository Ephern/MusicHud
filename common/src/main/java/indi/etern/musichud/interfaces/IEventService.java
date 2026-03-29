package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.architectury.event.ModEventService;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public interface IEventService {
    static void setInstance(IEventService eventService) {
        InstanceHolder.instance = eventService;
    }

    static IEventService getInstance() {
        IEventService registered = InstanceHolder.instance;
        if (registered != null) {
            return registered;
        }
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ModEventService.getInstance();
            }
        }
        throw new UnsupportedOperationException();
    }

    static void init() {
        getInstance().initialize();
    }

    void initialize();

    void registerClientPlayerJoin(Consumer<LocalPlayer> listener);
    void registerClientPlayerQuit(Consumer<LocalPlayer> listener);
    void registerClientTickPost(Runnable listener);
    void registerClientLifecycleStopping(Runnable listener);
    void registerCommonPlayerQuit(Consumer<ServerPlayer> listener);
    void registerServerLifecycleStopping(Runnable listener);

    final class InstanceHolder {
        private static IEventService instance;

        private InstanceHolder() {
        }
    }
}
