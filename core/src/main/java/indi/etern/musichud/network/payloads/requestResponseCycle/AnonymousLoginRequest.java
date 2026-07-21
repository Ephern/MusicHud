package indi.etern.musichud.network.payloads.requestResponseCycle;

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
public class AnonymousLoginRequest implements C2SPayload {
    public static final AnonymousLoginRequest REQUEST = new AnonymousLoginRequest();
    public static final ByteBufCodec<AnonymousLoginRequest> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {

        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    AnonymousLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((anonymousLoginRequest, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginAsAnonymous(player, true);
                    })
            );
        }
    }

}
