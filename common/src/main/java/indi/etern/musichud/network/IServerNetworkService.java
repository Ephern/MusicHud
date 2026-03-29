package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public interface IServerNetworkService {
    void sendToPlayer(ServerPlayer player, S2CPayload payload);
    void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload);

    static void setInstance(IServerNetworkService serverNetworkService) {
        InstanceHolder.instance = serverNetworkService;
    }

    static IServerNetworkService getInstance() {
        IServerNetworkService registered = InstanceHolder.instance;
        if (registered != null) {
            return registered;
        }
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ModNetworkManager.getInstance();
            }
        }
        throw new UnsupportedOperationException();
    }

    final class InstanceHolder {
        private static IServerNetworkService instance;

        private InstanceHolder() {
        }
    }
}
