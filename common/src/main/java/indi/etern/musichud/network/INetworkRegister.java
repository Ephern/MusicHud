package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.IPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface INetworkRegister {
    <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> serverReceiver
    );
    <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver
    );
    <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientOrServerReceiver
    );
    <T extends IPayload> CustomPacketPayload.Type<T> getType(Class<T> customPacketPayloadClass);

    static INetworkRegister getInstance() {
        switch (MusicHud.getCurrentEnvironment().getPlatform()) {
            case FABRIC, NEOFORGE -> {
                return ModNetworkManager.getInstance();
            }
            case PAPER -> {
                return ReflectionHolder.load("indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager", INetworkRegister.class);
            }
        }
        throw new UnsupportedOperationException();
    }

    final class ReflectionHolder {
        private ReflectionHolder() {
        }

        static <T> T load(String className, Class<T> expectedType) {
            try {
                Object instance = Class.forName(className).getMethod("getInstance").invoke(null);
                return expectedType.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
