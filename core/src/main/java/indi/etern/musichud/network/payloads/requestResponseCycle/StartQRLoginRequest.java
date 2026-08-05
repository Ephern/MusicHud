package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class StartQRLoginRequest extends ApiRequestPayload {
    public static final StartQRLoginRequest REQUEST = new StartQRLoginRequest();
    public static final ByteBufCodec<StartQRLoginRequest> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(
                    StartQRLoginRequest.class, CODEC,
                    (request, player) -> {
                        var qrLoginInfo = ILoginApiService.getInstance(ApiProvider.NCM).startQRLoginByPlayer(player);
                        return ResponseResult.of(new StartQRLoginResponse(qrLoginInfo.data().qrimg()));
                    }
            );
        }
    }
}