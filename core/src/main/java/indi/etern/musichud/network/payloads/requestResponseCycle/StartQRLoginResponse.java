package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.Getter;

@Getter
public final class StartQRLoginResponse extends ApiResponsePayload {
    public static final ByteBufCodec<StartQRLoginResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8,
                    StartQRLoginResponse::getBase64QRImg,
                    StartQRLoginResponse::new
            );
    private final String base64QRImg;

    public StartQRLoginResponse(String base64QRImg) {
        this.base64QRImg = base64QRImg;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    StartQRLoginResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
