package indi.etern.musichud.client.network.vanilla;

import indi.etern.musichud.network.payloads.IPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@AllArgsConstructor
public class CustomPacketPayloadWrapper<T extends IPayload> implements CustomPacketPayload {
    @Getter
    T payload;

    @Override
    @NonNull
    public Type<? extends CustomPacketPayloadWrapper<? extends IPayload>> type() {
        return IVanillaNetworkRegister.getMetaDataOrNew(payload.getClass(), null).type();
    }
}
