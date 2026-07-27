package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record EmailPasswordLoginRequest(String email, String md5password) implements C2SPayload {
    public static final ByteBufCodec<EmailPasswordLoginRequest> CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8,
                    EmailPasswordLoginRequest::email,
                    Codecs.STRING_UTF8,
                    EmailPasswordLoginRequest::md5password,
                    EmailPasswordLoginRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    EmailPasswordLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginWithEmailAndPassword(request.email,request.md5password,player);
                    })
            );
        }
    }
}