package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IClientEventService {
    static IClientEventService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IClientEventService> supplier = platform.getClientEventServiceSupplier();
        if (supplier != null) {
            IClientEventService iClientEventService = supplier.get();
            if (iClientEventService != null) {
                return iClientEventService;
            }
        }
        throw new UnsupportedOperationException();
    }

    void registerClientPlayerJoin(Consumer<Player> listener);
    void registerClientPlayerQuit(Consumer<Player> listener);
    void registerClientTickPost(Runnable listener);
    void registerClientLifecycleStopping(Runnable listener);
}
