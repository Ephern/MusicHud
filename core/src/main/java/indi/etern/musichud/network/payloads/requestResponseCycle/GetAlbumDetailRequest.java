package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
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
public class GetAlbumDetailRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetAlbumDetailRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetAlbumDetailRequest::getId,
                    Codecs.BOOL,
                    GetAlbumDetailRequest::isIgnoreCache,
                    GetAlbumDetailRequest::new
            )
    );

    private final long id;
    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetAlbumDetailRequest.class, CODEC, (request, player) -> {
                Album album = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getAlbumInfoDetail(request.getId(), request.isIgnoreCache(), player.getUUID());
                if (album != null) {
                    return ResponseResult.of(new GetAlbumDetailResponse(album));
                }
                return ResponseResult.ignore();
            });
        }
    }
}
