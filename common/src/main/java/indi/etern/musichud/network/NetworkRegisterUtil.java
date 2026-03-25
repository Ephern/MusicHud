package indi.etern.musichud.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import net.fabricmc.api.EnvType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class NetworkRegisterUtil {
    public static <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> serverReceiver
    ) {
        EnvType envType = Platform.getEnv();
        CustomPacketPayload.Type<T> type = getType(clazz);
        if (envType == EnvType.CLIENT && !ClientConfigDefinition.enableEmbeddedServer.get()) {
            //客户端需注册一个空的Receiver
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
                    serverReceiver
            );
        }
    }

    public static <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> clientReceiver
    ) {
        EnvType envType = Platform.getEnv();
        CustomPacketPayload.Type<T> type = getType(clazz);
        if (envType == EnvType.CLIENT) {
            NetworkManager.registerReceiver(
                    NetworkManager.Side.S2C,
                    type,
                    codec,
                    clientReceiver
            );
        } else {
            NetworkManager.registerS2CPayloadType(type, codec);
        }
    }

    public static <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkManager.NetworkReceiver<T> clientOrServerReceiver
    ) {
        if (S2CPayload.class.isAssignableFrom(clazz)) {
            registerS2CPayload(clazz, codec, clientOrServerReceiver);
        } else if (C2SPayload.class.isAssignableFrom(clazz)){
            registerC2SPayload(clazz, codec, clientOrServerReceiver);
        } else {
            throw new IllegalArgumentException("Payload class must implements S2CPayload or C2SPayload");
        }
    }

    static Map<Class<? extends IPayload>, CustomPacketPayload.Type<? extends IPayload>> typeMap = new HashMap<>();

    public static <T extends IPayload> CustomPacketPayload.Type<T> getType(Class<T> customPacketPayloadClass) {
        if (typeMap.get(customPacketPayloadClass) != null) {
            //noinspection unchecked
            return (CustomPacketPayload.Type<T>) typeMap.get(customPacketPayloadClass);
        }
        String name = String.join("_", StringUtils.splitByCharacterTypeCamelCase(customPacketPayloadClass.getSimpleName())).toLowerCase();
        CustomPacketPayload.Type<T> customPacketPayloadType = new CustomPacketPayload.Type<>(MusicHud.location(name));
        typeMap.put(customPacketPayloadClass, customPacketPayloadType);
        return customPacketPayloadType;
    }
}