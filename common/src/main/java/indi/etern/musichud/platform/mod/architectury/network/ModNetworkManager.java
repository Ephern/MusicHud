package indi.etern.musichud.platform.mod.architectury.network;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModNetworkManager implements INetworkRegister, IServerNetworkService, IClientNetworkService {
    private static volatile ModNetworkManager instance;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    public static ModNetworkManager getInstance() {
        if (instance == null) {
            synchronized (ModNetworkManager.class) {
                if (instance == null) {
                    instance = new ModNetworkManager();
                }
            }
        }
        return instance;
    }

    static Map<Class<? extends IPayload>, CustomPacketPayload.Type<? extends IPayload>> typeMap = new HashMap<>();

    public <T extends IPayload> CustomPacketPayload.Type<T> getType(Class<T> customPacketPayloadClass) {
        if (typeMap.get(customPacketPayloadClass) != null) {
            //noinspection unchecked
            return (CustomPacketPayload.Type<T>) typeMap.get(customPacketPayloadClass);
        }
        String name = String.join("_", StringUtils.splitByCharacterTypeCamelCase(customPacketPayloadClass.getSimpleName())).toLowerCase();
        CustomPacketPayload.Type<T> customPacketPayloadType = new CustomPacketPayload.Type<>(MusicHud.location(name));
        typeMap.put(customPacketPayloadClass, customPacketPayloadType);
        return customPacketPayloadType;
    }

    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        CustomPacketPayload.Type<T> type = getType(clazz);
        if (side == Environment.Side.CLIENT && !clientConfig.getEnableEmbeddedServer()) {
            NetworkManager.registerReceiver(
                    NetworkManager.Side.C2S,
                    type,
                    codec,
                    (t, p) -> {
                    }
            );
        } else {
            NetworkManager.registerReceiver(
                    NetworkManager.Side.C2S,
                    type,
                    codec,
                    (t,p) -> serverReceiver.receive(t, p.getPlayer())
            );
        }
    }

    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        CustomPacketPayload.Type<T> type = getType(clazz);
        if (side == Environment.Side.CLIENT) {
            NetworkManager.registerReceiver(
                    NetworkManager.Side.S2C,
                    type,
                    codec,
                    (t,p) -> clientReceiver.receive(t, p.getPlayer())
            );
        } else {
            NetworkManager.registerS2CPayloadType(type, codec);
        }
    }

    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientOrServerReceiver
    ) {
        if (S2CPayload.class.isAssignableFrom(clazz)) {
            registerS2CPayload(clazz, codec, clientOrServerReceiver);
        } else if (C2SPayload.class.isAssignableFrom(clazz)) {
            registerC2SPayload(clazz, codec, clientOrServerReceiver);
        } else {
            throw new IllegalArgumentException("Payload class must implements S2CPayload or C2SPayload");
        }
    }

    @Override
    public void sendToServer(C2SPayload payload) {
        NetworkManager.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, S2CPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload) {
        NetworkManager.sendToPlayers(players, payload);
    }
}