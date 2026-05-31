package indi.etern.musichud.platform.mod.fabric.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class FabricNetworkRegister implements INetworkRegister {
    private static volatile FabricNetworkRegister instance;
    private final Map<Class<? extends IPayload>, CustomPacketPayload.Type<?>> typeMap = new ConcurrentHashMap<>();

    public static FabricNetworkRegister getInstance() {
        if (instance == null) {
            synchronized (FabricNetworkRegister.class) {
                if (instance == null) {
                    instance = new FabricNetworkRegister();
                }
            }
        }
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IPayload> CustomPacketPayload.Type<T> getType(Class<T> customPacketPayloadClass) {
        return (CustomPacketPayload.Type<T>) typeMap.computeIfAbsent(customPacketPayloadClass, clazz -> {
            String name = String.join("_", StringUtils.splitByCharacterTypeCamelCase(clazz.getSimpleName())).toLowerCase();
            return new CustomPacketPayload.Type<>(MusicHud.location(name));
        });
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        CustomPacketPayload.Type<T> type = getType(clazz);
//        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        PayloadTypeRegistry.serverboundPlay().register(type, codec);

        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();

        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            serverReceiver.receive(payload, context.player());
        });
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        CustomPacketPayload.Type<T> type = getType(clazz);
        PayloadTypeRegistry.clientboundPlay().register(type, codec);

        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            FabricClientNetworkRegisterUtil.register(type, clientReceiver);
        }
    }

    @Override
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
}