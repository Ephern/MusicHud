package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.platform.Environment;

import java.util.function.Supplier;

public interface IClientNetworkService {
    void sendToServer(C2SPayload payload);

    static IClientNetworkService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IClientNetworkService> supplier = platform.getClientNetworkServiceSupplier();
        if (supplier != null) {
            IClientNetworkService clientNetworkService = supplier.get();
            if (clientNetworkService != null) {
                return clientNetworkService;
            }
        }
        throw new UnsupportedOperationException();
    }
}
