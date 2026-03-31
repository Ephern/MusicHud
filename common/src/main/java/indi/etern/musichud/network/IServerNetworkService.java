package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.function.Supplier;

public interface IServerNetworkService {
    void sendToPlayer(ServerPlayer player, S2CPayload payload);
    void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload);

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
}
