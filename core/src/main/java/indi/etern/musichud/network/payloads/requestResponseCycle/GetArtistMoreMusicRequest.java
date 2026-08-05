package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetArtistMoreMusicRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetArtistMoreMusicRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetArtistMoreMusicRequest::getId,
                    Codecs.INT,
                    GetArtistMoreMusicRequest::getOffset,
                    GetArtistMoreMusicRequest::new
            )
    );

    private final long id;
    private final int offset;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetArtistMoreMusicRequest.class, CODEC, (request, player) -> {
                List<MusicDetail> musicDetails = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getArtistMoreMusic(request.getId(), request.getOffset(), player.getUUID());
                if (musicDetails != null) {
                    return ResponseResult.of(new GetArtistMoreMusicResponse(request.getId(), request.getOffset(), musicDetails));
                }
                return ResponseResult.ignore();
            });
        }
    }
}
