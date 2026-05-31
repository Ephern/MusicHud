package indi.etern.musichud.network.payloads;

import indi.etern.musichud.network.INetworkRegister;
import lombok.NonNull;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface IPayload extends CustomPacketPayload {
    @Override
    @NonNull
    default Type<? extends IPayload> type() {
        return INetworkRegister.getInstance().getType(getClass());
    }
}
