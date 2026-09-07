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
                // Deliver inline on the caller's thread. Loopback sends are made in causal
                // order (a response precedes the pushes it triggers, a snapshot is built
                // right before it is sent), and synchronous delivery preserves that order.
                // A per-message async hop on the shared executor allowed out-of-order
                // processing, which let a stale snapshot be reconciled against the client's
                // local idle-source layer after newer adds had already completed, wiping
                // sources the server still held.
                receiver.receive(payload, player);
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