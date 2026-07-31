package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SendPhoneValidationCodeResponse extends ApiResponsePayload {
    public static final ByteBufCodec<SendPhoneValidationCodeResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    SendPhoneValidationCodeResponse::isSuccess,
                    Codecs.INT,
                    SendPhoneValidationCodeResponse::getTimeout,
                    SendPhoneValidationCodeResponse::new
            )
    );

    private final boolean success;
    private final int timeout;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SendPhoneValidationCodeResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
