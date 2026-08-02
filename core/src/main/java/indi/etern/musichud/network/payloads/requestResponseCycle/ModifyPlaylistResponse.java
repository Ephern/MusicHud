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
public class ModifyPlaylistResponse extends ApiResponsePayload {
    public static final ByteBufCodec<ModifyPlaylistResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    ModifyPlaylistResponse::isSuccess,
                    Codecs.STRING_UTF8,
                    ModifyPlaylistResponse::getMessage,
                    ModifyPlaylistResponse::new
            )
    );

    private final boolean success;
    private final String message;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(ModifyPlaylistResponse.class, CODEC,
                    (response, playerClient) -> RequestResponseManager.complete(response)
            );
        }
    }
}
