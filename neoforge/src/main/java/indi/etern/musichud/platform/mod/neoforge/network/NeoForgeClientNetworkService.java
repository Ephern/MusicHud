package indi.etern.musichud.platform.mod.neoforge.network;

import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@SuppressWarnings("unused")
public class NeoForgeClientNetworkService implements IClientNetworkService {
    @Override
    public void sendToNetworkServer(C2SPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
