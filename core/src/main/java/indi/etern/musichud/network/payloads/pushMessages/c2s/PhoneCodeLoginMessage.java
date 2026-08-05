package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record PhoneCodeLoginMessage(int regionCode, long phone, int code) implements C2SPayload {
    public static final ByteBufCodec<PhoneCodeLoginMessage> CODEC =
            ByteBufCodec.composite(
                    Codecs.INT,
                    PhoneCodeLoginMessage::regionCode,
                    Codecs.LONG,
                    PhoneCodeLoginMessage::phone,
                    Codecs.INT,
                    PhoneCodeLoginMessage::code,
                    PhoneCodeLoginMessage::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    PhoneCodeLoginMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).loginWithPhoneAndCode(request.regionCode, request.phone, request.code,player);
                    })
            );
        }
    }
}