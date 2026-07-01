package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import indi.etern.musichud.platform.Environment;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

public interface IClientNetworkService {
    void sendToNetworkServer(C2SPayload payload);

    default <T extends C2SPayload> void sendToServer(T payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null && (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED
                || payload instanceof ConnectRequest)) {
            sendToNetworkServer(payload);
        } else if ((minecraft.getCurrentServer() != null || minecraft.player != null)
                && ClientConfig.getInstance().getEnableIsolatedMode()){// in single player game or isolated client
            //noinspection unchecked
            NetworkReceiver<T> receiver = (NetworkReceiver<T>) INetworkRegister.getInstance()
                    .getMetaDataOrNew(payload.getClass(), null).receiver();
            if (receiver != null) {
                receiver.receive(payload, minecraft.player);
            }
        }
    }

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
