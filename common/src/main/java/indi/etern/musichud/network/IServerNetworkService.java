package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import indi.etern.musichud.utils.ClientDistUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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

    void sendToNetworkPlayer(ServerPlayer player, S2CPayload payload);

    default <T extends S2CPayload> void sendToPlayer(Player player, T payload) {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && ClientDistUtil.isLocalPlayer(player)) {
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
        } else if (player instanceof ServerPlayer serverPlayer) {
            sendToNetworkPlayer(serverPlayer, payload);
        } else {
            throw new IllegalStateException();
        }
    }

    default void sendToPlayers(Collection<Player> players, S2CPayload payload) {
        for (Player player : players) {
            sendToPlayer(player, payload);
        }
    }

    default void sendToPlayerInfos(Collection<LoginApiService.PlayerLoginInfo> playerLoginInfos, S2CPayload payload) {
        for (LoginApiService.PlayerLoginInfo playerLoginInfo : playerLoginInfos) {
            Player player = playerLoginInfo.getPlayer();
            if (player != null) {
                sendToPlayer(player, payload);
            }
        }
    }
}
