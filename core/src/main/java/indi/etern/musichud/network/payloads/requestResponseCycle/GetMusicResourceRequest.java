package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetMusicResourceRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetMusicResourceRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetMusicResourceRequest::getId,
                    Codecs.ofEnum(Quality.class),
                    GetMusicResourceRequest::getQuality,
                    Codecs.STRING_UTF8,
                    GetMusicResourceRequest::getRetryForUrl,
                    GetMusicResourceRequest::new
            )
    );

    private final long id;
    private final Quality quality;
    private final String retryForUrl;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetMusicResourceRequest.class, CODEC, (request, player) -> {
                var currentMusicResourceInfo = MusicPlayerServerService.getInstance()
                        .getMusicResourceInfo(request.getId(), request.getQuality(), request.getRetryForUrl(), player.getUUID());
                return ResponseResult.of(new GetMusicResourceResponse(currentMusicResourceInfo));
            });
        }
    }
}
