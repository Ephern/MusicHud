package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.architectury.event.ModEventService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

public interface IEventService {
    static IEventService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ModEventService.getInstance();
            }
            case PAPER -> {
                return ReflectionHolder.load("indi.etern.musichud.platform.plugin.paper.event.PaperEventService", IEventService.class);
            }
        }
        throw new UnsupportedOperationException();
    }

    static void init() {
        getInstance().initialize();
    }

    void initialize();

    void registerClientPlayerJoin(Consumer<Player> listener);
    void registerClientPlayerQuit(Consumer<Player> listener);
    void registerClientTickPost(Runnable listener);
    void registerClientLifecycleStopping(Runnable listener);
    void registerCommonPlayerQuit(Consumer<ServerPlayer> listener);
    void registerServerLifecycleStopping(Runnable listener);

    final class ReflectionHolder {
        private ReflectionHolder() {
        }

        static <T> T load(String className, Class<T> expectedType) {
            try {
                Object instance = Class.forName(className).getMethod("getInstance").invoke(null);
                return expectedType.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
