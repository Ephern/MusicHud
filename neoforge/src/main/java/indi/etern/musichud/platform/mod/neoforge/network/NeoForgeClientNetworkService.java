package indi.etern.musichud.platform.mod.neoforge.network;

import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("unused")
public class NeoForgeClientNetworkService implements IClientNetworkService {
    private static volatile NeoForgeClientNetworkService instance;

    public static NeoForgeClientNetworkService getInstance() {
        if (instance == null) {
            synchronized (NeoForgeClientNetworkService.class) {
                if (instance == null) {
                    instance = new NeoForgeClientNetworkService();
                }
            }
        }
        return instance;
    }

    @Override
    public void sendToNetworkServer(C2SPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
