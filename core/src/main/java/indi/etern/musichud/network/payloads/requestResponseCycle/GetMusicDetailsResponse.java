package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetMusicDetailsResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetMusicDetailsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.ofList(() -> MusicDetail.CODEC)), GetMusicDetailsResponse::getResult,
                    GetMusicDetailsResponse::new
            )
    );

    private final MessagedResult<List<MusicDetail>> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetMusicDetailsResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
