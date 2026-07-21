package indi.etern.musichud.client.network.vanilla;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

public interface VanillaServerNetworkService extends IServerNetworkService {
    void sendToNetworkPlayer(IPlayerClient player, S2CPayload payload);

    @Override
    default <T extends S2CPayload> void sendToPlayer(IPlayerClient player, T payload) {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && player.getClientType() == IPlayerClient.ClientType.LOCAL) {
            //noinspection unchecked
            NetworkReceiver<T> receiver = (NetworkReceiver<T>) IVanillaNetworkRegister
                    .getMetaDataOrNew(payload.getClass(), null).receiver();
            if (receiver != null) {
                MusicHud.EXECUTOR.execute(() -> {
                    receiver.receive(payload, player);
                });
            } else {
                throw new IllegalStateException();
            }
        } else if (player.getClientType() == IPlayerClient.ClientType.REMOTE) {
            sendToNetworkPlayer(player, payload);
        } else {
            throw new IllegalStateException();
        }
    }
}