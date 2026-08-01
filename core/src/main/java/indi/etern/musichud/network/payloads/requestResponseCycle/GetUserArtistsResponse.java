package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashSet;

@Getter
@AllArgsConstructor
public class GetUserArtistsResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetUserArtistsResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.ofCollection(LinkedHashSet::new,() -> Artist.CODEC),
                    GetUserArtistsResponse::getArtists,
                    GetUserArtistsResponse::new
            )
    );

    private final LinkedHashSet<Artist> artists;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserArtistsResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
