package indi.etern.musichud.platform.mod.fabric.network;

import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class FabricClientNetworkService implements IClientNetworkService {
    private static volatile FabricClientNetworkService instance;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    private final Map<Class<? extends IPayload>, CustomPacketPayload.Type<?>> typeMap = new ConcurrentHashMap<>();

    public static FabricClientNetworkService getInstance() {
        if (instance == null) {
            synchronized (FabricClientNetworkService.class) {
                if (instance == null) {
                    instance = new FabricClientNetworkService();
                }
            }
        }
        return instance;
    }

    @Override
    public void sendToServer(C2SPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}