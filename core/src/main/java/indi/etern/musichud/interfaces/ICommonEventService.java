package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.platform.Environment;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ICommonEventService {
    static ICommonEventService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<ICommonEventService> supplier = platform.getServerEventServiceSupplier();
        if (supplier != null) {
            ICommonEventService iCommonEventService = supplier.get();
            if (iCommonEventService != null) {
                return iCommonEventService;
            }
        }
        throw new UnsupportedOperationException();
    }

    Unregister registerCommonPlayerQuit(Consumer<IPlayerClient> listener);
    Unregister registerCommonLifecycleStopping(Runnable listener);
}
