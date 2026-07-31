package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SendPhoneValidationCodeRequest extends ApiRequestPayload {
    public static final ByteBufCodec<SendPhoneValidationCodeRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT,
                    SendPhoneValidationCodeRequest::getRegionCode,
                    Codecs.LONG,
                    SendPhoneValidationCodeRequest::getPhone,
                    SendPhoneValidationCodeRequest::new
            )
    );

    private final int regionCode;
    private final long phone;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(SendPhoneValidationCodeRequest.class, CODEC, (request, player) ->
                    ResponseResult.of(ILoginApiService.getInstance(ApiProvider.NCM)
                            .requestValidationCodeFor(request.getRegionCode(), request.getPhone(), player))
            );
        }
    }
}
