package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SubscribeResponse extends ApiResponsePayload {
    public static final ByteBufCodec<SubscribeResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    SubscribeResponse::isSuccess,
                    Codecs.STRING_UTF8,
                    SubscribeResponse::getMessage,
                    SubscribeResponse::new
            )
    );

    private final boolean success;
    private final String message;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SubscribeResponse.class, CODEC,
                    (response, playerClient) -> RequestResponseManager.complete(response)
            );
        }
    }
}
