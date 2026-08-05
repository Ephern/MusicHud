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

    <T extends S2CPayload> void sendToPlayer(IPlayerClient player, T payload);

    default void sendToPlayers(Collection<IPlayerClient> players, S2CPayload payload) {
        for (IPlayerClient player : players) {
            sendToPlayer(player, payload);
        }
    }

    default void sendToChannel(String channel, S2CPayload payload) {
        for (IPlayerClient player : ChannelManager.getSubscribers(channel)) {
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
