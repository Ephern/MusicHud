package indi.etern.musichud.network;

import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public interface IServerNetworkService {
    void sendToPlayer(ServerPlayer player, S2CPayload payload);
    void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload);

    static IServerNetworkService getInstance() {
        try {
            Class.forName("dev.architectury.networking.NetworkManager");
            return ModNetworkManager.getInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
