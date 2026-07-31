package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
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
public class GetArtistDetailResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetArtistDetailResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Artist.CODEC,
                    GetArtistDetailResponse::getArtist,
                    GetArtistDetailResponse::new
            )
    );

    private final Artist artist;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetArtistDetailResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
