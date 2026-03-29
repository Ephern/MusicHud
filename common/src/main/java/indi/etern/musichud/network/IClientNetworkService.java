package indi.etern.musichud.network;

import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.C2SPayload;

public interface IClientNetworkService {
    void sendToServer(C2SPayload payload);

    static IClientNetworkService getInstance() {
        try {
            Class.forName("dev.architectury.networking.NetworkManager");
            return ModNetworkManager.getInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}