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

public record PhoneCodeLoginRequest(int regionCode, long phone, int code) implements C2SPayload {
    public static final ByteBufCodec<PhoneCodeLoginRequest> CODEC =
            ByteBufCodec.composite(
                    Codecs.INT,
                    PhoneCodeLoginRequest::regionCode,
                    Codecs.VAR_LONG,//TODO replace with Codecs.LONG in 1.3.0
                    PhoneCodeLoginRequest::phone,
                    Codecs.INT,
                    PhoneCodeLoginRequest::code,
                    PhoneCodeLoginRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    PhoneCodeLoginRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginWithPhoneAndCode(request.regionCode, request.phone, request.code,player);
                    })
            );
        }
    }
}