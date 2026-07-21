package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.platform.Environment;
import java.util.function.Supplier;

public interface INetworkRegister {
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
}
