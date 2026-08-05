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

@Getter
@AllArgsConstructor
public class GetArtistDetailRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetArtistDetailRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetArtistDetailRequest::getId,
                    GetArtistDetailRequest::new
            )
    );

    private final long id;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetArtistDetailRequest.class, CODEC, (request, player) -> {
                Artist artistDetail = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getArtistDetail(request.getId(), player.getUUID());
                if (artistDetail != null) {
                    return ResponseResult.of(new GetArtistDetailResponse(artistDetail));
                }
                return ResponseResult.ignore();
            });
        }
    }
}
