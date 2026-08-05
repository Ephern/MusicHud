package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class CancelQRLoginMessage implements C2SPayload {
    public static final CancelQRLoginMessage REQUEST = new CancelQRLoginMessage();
    public static final ByteBufCodec<CancelQRLoginMessage> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    CancelQRLoginMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((cancelQRLoginRequest, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).cancelQRLoginByPlayer(player);
                    })
            );
        }
    }

}
