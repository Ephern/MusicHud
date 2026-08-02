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
public class AnonymousLoginMessage implements C2SPayload {
    public static final AnonymousLoginMessage REQUEST = new AnonymousLoginMessage();
    public static final ByteBufCodec<AnonymousLoginMessage> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {

        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    AnonymousLoginMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((anonymousLoginMessage, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginAsAnonymous(player, true);
                    })
            );
        }
    }

}
