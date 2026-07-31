package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
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

import java.util.List;

@Getter
@AllArgsConstructor
public class GetArtistMoreMusicResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetArtistMoreMusicResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetArtistMoreMusicResponse::getArtistId,
                    Codecs.INT,
                    GetArtistMoreMusicResponse::getOffset,
                    Codecs.ofList(() -> MusicDetail.CODEC),
                    GetArtistMoreMusicResponse::getMusicDetails,
                    GetArtistMoreMusicResponse::new
            )
    );

    private final long artistId;
    private final int offset;
    private final List<MusicDetail> musicDetails;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetArtistMoreMusicResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
