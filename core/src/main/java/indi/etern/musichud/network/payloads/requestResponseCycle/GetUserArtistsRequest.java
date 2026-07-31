package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
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
public class GetUserArtistsRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetUserArtistsRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    GetUserArtistsRequest::isIgnoreCache,
                    GetUserArtistsRequest::new
            )
    );

    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserArtistsRequest.class, CODEC, (request, player) -> {
                List<Artist> playersUserArtists = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getPlayersUserSubscribedArtists(request.isIgnoreCache(), player.getUUID());
                return ResponseResult.of(new GetUserArtistsResponse(playersUserArtists));
            });
        }
    }
}
