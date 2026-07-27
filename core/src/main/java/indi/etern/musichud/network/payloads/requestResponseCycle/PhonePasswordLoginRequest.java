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

public record PhonePasswordLoginRequest(long phone, String md5password) implements C2SPayload {
    public static final ByteBufCodec<PhonePasswordLoginRequest> CODEC =
            ByteBufCodec.composite(
                    Codecs.LONG,
                    PhonePasswordLoginRequest::phone,
                    Codecs.STRING_UTF8,
                    PhonePasswordLoginRequest::md5password,
                    PhonePasswordLoginRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    PhonePasswordLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginWithPhoneAndPassword(request.phone,request.md5password,player);
                    })
            );
        }
    }
}