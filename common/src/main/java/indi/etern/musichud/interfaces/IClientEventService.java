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

    Unregister registerClientPlayerJoin(Consumer<Player> listener);
    Unregister registerClientPlayerQuit(Consumer<Player> listener);
    Unregister registerClientTickPost(Runnable listener);
    Unregister registerClientLifecycleStopping(Runnable listener);
}