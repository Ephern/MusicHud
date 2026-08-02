package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashSet;

@Getter
@AllArgsConstructor
public class GetUserAlbumsRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetUserAlbumsRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    GetUserAlbumsRequest::isIgnoreCache,
                    GetUserAlbumsRequest::new
            )
    );

    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserAlbumsRequest.class, CODEC, (request, player) -> {
                LinkedHashSet<Album> playersUserAlbums = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getPlayersUserSubscribedAlbums(request.isIgnoreCache(), player.getUUID());
                return ResponseResult.of(new GetUserAlbumsResponse(playersUserAlbums));
            });
        }
    }
}
