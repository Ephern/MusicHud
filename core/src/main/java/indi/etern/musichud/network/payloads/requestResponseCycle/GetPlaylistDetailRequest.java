package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetPlaylistDetailRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetPlaylistDetailRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetPlaylistDetailRequest::getId,
                    Codecs.BOOL,
                    GetPlaylistDetailRequest::isIgnoreCache,
                    GetPlaylistDetailRequest::new
            )
    );

    private final long id;
    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetPlaylistDetailRequest.class, CODEC, (request, player) -> {
                Playlist playlistDetail = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getPlaylistDetail(request.getId(), request.isIgnoreCache(), player.getUUID());
                return ResponseResult.of(new GetPlaylistDetailResponse(playlistDetail));
            });
        }
    }
}
