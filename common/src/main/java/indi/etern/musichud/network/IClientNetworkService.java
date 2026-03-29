package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.PlatformServiceRegistry;
import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.C2SPayload;

public interface IClientNetworkService {
    void sendToServer(C2SPayload payload);

    static IClientNetworkService getInstance() {
        IClientNetworkService registered = PlatformServiceRegistry.getClientNetworkService();
        if (registered != null) {
            return registered;
        }
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ModNetworkManager.getInstance();
            }
        }
        throw new UnsupportedOperationException();
    }
}
