package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IServerEventService {
    static IServerEventService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IServerEventService> supplier = platform.getServerEventServiceSupplier();
        if (supplier != null) {
            IServerEventService iServerEventService = supplier.get();
            if (iServerEventService != null) {
                return iServerEventService;
            }
        }
        throw new UnsupportedOperationException();
    }

    Unregister registerCommonPlayerQuit(Consumer<ServerPlayer> listener);
    Unregister registerServerLifecycleStopping(Runnable listener);
}
