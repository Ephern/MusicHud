package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.UserCategoryPlaylists;
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
public class GetUserPlaylistResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetUserPlaylistResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    UserCategoryPlaylists.CODEC,
                    GetUserPlaylistResponse::getPlaylists,
                    GetUserPlaylistResponse::new
            )
    );

    private final UserCategoryPlaylists playlists;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserPlaylistResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
