package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashSet;

@Getter
@AllArgsConstructor
public class GetUserAlbumsResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetUserAlbumsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.ofCollection(LinkedHashSet::new, () -> Album.CODEC)),
                    GetUserAlbumsResponse::getResult,
                    GetUserAlbumsResponse::new
            )
    );

    private final MessagedResult<LinkedHashSet<Album>> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserAlbumsResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
