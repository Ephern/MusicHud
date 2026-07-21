package indi.etern.musichud.client.network.vanilla;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import net.minecraft.client.Minecraft;

public interface VanillaClientNetworkService extends IClientNetworkService {
    void sendToNetworkServer(C2SPayload payload);

    @Override
    default <T extends C2SPayload> void sendToServer(T payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null && (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED
                || payload instanceof ConnectRequest)) {
            sendToNetworkServer(payload);
        } else if ((minecraft.getCurrentServer() != null || minecraft.player != null)
                && ClientConfig.getInstance().getEnableIsolatedMode()){// in single player game or isolated client
            //noinspection unchecked
            NetworkReceiver<T> receiver = (NetworkReceiver<T>) IVanillaNetworkRegister.getMetaDataOrNew(payload.getClass(), null).receiver();
            if (receiver != null) {
                receiver.receive(payload, VanillaPlayerProxy.ofPlayer(minecraft.player));
            }
        }

    }
}
