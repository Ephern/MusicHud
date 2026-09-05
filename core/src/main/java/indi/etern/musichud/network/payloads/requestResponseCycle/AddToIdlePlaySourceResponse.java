package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Reply to {@link AddToIdlePlaySourceRequest}: message is a {@code music_hud.} i18n key on failure. */
@Getter
@AllArgsConstructor
public class AddToIdlePlaySourceResponse extends ApiResponsePayload {
    public static final ByteBufCodec<AddToIdlePlaySourceResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.VOID),
                    AddToIdlePlaySourceResponse::getResult,
                    AddToIdlePlaySourceResponse::new
            )
    );

    private final MessagedResult<Void> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(AddToIdlePlaySourceResponse.class, CODEC,
                    (response, playerClient) -> RequestResponseManager.complete(response)
            );
        }
    }
}
