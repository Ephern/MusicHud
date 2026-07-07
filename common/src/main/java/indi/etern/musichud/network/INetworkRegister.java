package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public interface INetworkRegister {
    record PayloadMetadata<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, NetworkReceiver<?> receiver){}
    Map<Class<? extends IPayload>, PayloadMetadata<?>> metadataMap = new ConcurrentHashMap<>();

    static INetworkRegister getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<INetworkRegister> supplier = platform.getNetworkRegisterSupplier();
        if (supplier != null) {
            INetworkRegister networkRegister = supplier.get();
            if (networkRegister != null) {
                return networkRegister;
            }
        }
        throw new UnsupportedOperationException();
    }

    <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> serverReceiver
    );

    <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver
    );

    <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientOrServerReceiver
    );

    @SuppressWarnings("unchecked")
    default <T extends IPayload> PayloadMetadata<T> getMetaDataOrNew(Class<T> customPacketPayloadClass, @Nullable NetworkReceiver<T> networkReceiver) {
        return (PayloadMetadata<T>) metadataMap.computeIfAbsent(customPacketPayloadClass, clazz -> {
            if (networkReceiver == null) {
                throw new IllegalStateException("No pre-cached metadata, and networkReceiver is null");
            }
            String name = String.join("_", StringUtils.splitByCharacterTypeCamelCase(clazz.getSimpleName())).toLowerCase();
            return new PayloadMetadata<T>(new CustomPacketPayload.Type<>(MusicHud.location(name)), networkReceiver);
        });
    }
}
