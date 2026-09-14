package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class RotateNextToPlayResponse extends ApiResponsePayload {
    public static final ByteBufCodec<RotateNextToPlayResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.VOID),
                    RotateNextToPlayResponse::getResult,
                    RotateNextToPlayResponse::new
            )
    );
    MessagedResult<Void> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(RotateNextToPlayResponse.class, CODEC,
                    (response, playerClient) -> RequestResponseManager.complete(response)
            );
        }
    }
}
