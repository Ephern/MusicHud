package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
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
public class GetPlaylistDetailResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetPlaylistDetailResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Playlist.CODEC,
                    GetPlaylistDetailResponse::getPlaylist,
                    GetPlaylistDetailResponse::new
            )
    );

    private final Playlist playlist;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetPlaylistDetailResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
