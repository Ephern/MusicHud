package indi.etern.musichud.network.payloads;

import icyllis.modernui.annotation.NonNull;
import indi.etern.musichud.network.INetworkRegister;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface IPayload extends CustomPacketPayload {
    @Override
    @NonNull
    default Type<? extends IPayload> type() {
        return INetworkRegister.getInstance().getType(getClass());
    }
}
