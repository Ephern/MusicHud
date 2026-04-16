package indi.etern.musichud.platform.mod.fabric.network;

import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class FabricServerNetworkService implements IServerNetworkService {
    private static volatile FabricServerNetworkService instance;
    private final Map<Class<? extends IPayload>, CustomPacketPayload.Type<?>> typeMap = new ConcurrentHashMap<>();

    public static FabricServerNetworkService getInstance() {
        if (instance == null) {
            synchronized (FabricServerNetworkService.class) {
                if (instance == null) {
                    instance = new FabricServerNetworkService();
                }
            }
        }
        return instance;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, S2CPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload) {
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}