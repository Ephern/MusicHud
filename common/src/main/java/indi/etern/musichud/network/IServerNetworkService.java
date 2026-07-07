package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;

import java.util.Collection;
import java.util.function.Supplier;

public interface IServerNetworkService {
    static IServerNetworkService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IServerNetworkService> supplier = platform.getServerNetworkServiceSupplier();
        if (supplier != null) {
            IServerNetworkService serverNetworkService = supplier.get();
            if (serverNetworkService != null) {
                return serverNetworkService;
            }
        }
        throw new UnsupportedOperationException();
    }

    void sendToNetworkPlayer(IPlayerClient player, S2CPayload payload);

    default <T extends S2CPayload> void sendToPlayer(IPlayerClient player, T payload) {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && player.getClientType() == IPlayerClient.ClientType.LOCAL) {
            //noinspection unchecked
            NetworkReceiver<T> receiver = (NetworkReceiver<T>) INetworkRegister.getInstance()
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

    default void sendToPlayers(Collection<IPlayerClient> players, S2CPayload payload) {
        for (IPlayerClient player : players) {
            sendToPlayer(player, payload);
        }
    }

    default void sendToPlayerInfos(Collection<LoginApiService.PlayerLoginInfo> playerLoginInfos, S2CPayload payload) {
        for (LoginApiService.PlayerLoginInfo playerLoginInfo : playerLoginInfos) {
            IPlayerClient player = playerLoginInfo.getPlayer();
            if (player != null) {
                sendToPlayer(player, payload);
            }
        }
    }
}
