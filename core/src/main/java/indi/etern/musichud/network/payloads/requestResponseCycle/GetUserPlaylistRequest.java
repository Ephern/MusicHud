package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.UserCategoryPlaylists;
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
public class GetUserPlaylistRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetUserPlaylistRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    GetUserPlaylistRequest::isIgnoreCache,
                    GetUserPlaylistRequest::new
            )
    );

    private final boolean ignoreCache;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserPlaylistRequest.class, CODEC, (request, player) -> {
                UserCategoryPlaylists playersUserPlaylists = IMusicApiService.getInstance(ApiProvider.NCM)
                        .getPlayersUserPlaylists(request.isIgnoreCache(), player.getUUID());
                return ResponseResult.of(new GetUserPlaylistResponse(playersUserPlaylists));
            });
        }
    }
}
