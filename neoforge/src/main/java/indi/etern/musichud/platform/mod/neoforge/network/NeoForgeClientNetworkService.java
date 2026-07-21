package indi.etern.musichud.platform.mod.neoforge.network;

import indi.etern.musichud.client.network.vanilla.CustomPacketPayloadWrapper;
import indi.etern.musichud.client.network.vanilla.VanillaClientNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NeoForgeClientNetworkService implements VanillaClientNetworkService {
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
        ClientPacketDistributor.sendToServer(new CustomPacketPayloadWrapper<>(payload));
    }
}
