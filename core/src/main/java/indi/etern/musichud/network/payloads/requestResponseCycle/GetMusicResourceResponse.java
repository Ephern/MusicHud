package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetMusicResourceResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetMusicResourceResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MusicResourceInfo.CODEC,
                    GetMusicResourceResponse::getMusicResourceInfo,
                    GetMusicResourceResponse::new
            )
    );

    private final MusicResourceInfo musicResourceInfo;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetMusicResourceResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
