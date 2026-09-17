package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * On-demand fetch of track details by id. Client-driven so callers can load details lazily
 * (e.g. after a cloud upload finishes) instead of the server eagerly pushing them.
 * {@code cloudSource} marks the results as coming from the user's cloud drive so playback
 * uses the owning account's cookie.
 */
@Getter
@AllArgsConstructor
public class GetMusicDetailsRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetMusicDetailsRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.ofList(() -> Codecs.LONG), GetMusicDetailsRequest::getIds,
                    Codecs.BOOL, GetMusicDetailsRequest::isCloudSource,
                    GetMusicDetailsRequest::new
            )
    );

    private final List<Long> ids;
    private final boolean cloudSource;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetMusicDetailsRequest.class, CODEC, (request, player) -> {
                try {
                    List<MusicDetail> details = IMusicApiService.getInstance(ApiProvider.NCM)
                            .getMusicDetailByIds(request.ids, player.getUUID());
                    if (request.cloudSource) {
                        // getMusicDetailByIds caches the same instances it returns, so this also
                        // updates the cache used by playback.
                        for (MusicDetail detail : details) {
                            detail.setExtraInfo(new MusicDetail.ExtraInfo(true, 0, false));
                        }
                    }
                    return ResponseResult.of(new GetMusicDetailsResponse(MessagedResult.success(details)));
                } catch (Throwable e) {
                    return ResponseResult.of(new GetMusicDetailsResponse(
                            MessagedResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage(), List.of())));
                }
            });
        }
    }
}
