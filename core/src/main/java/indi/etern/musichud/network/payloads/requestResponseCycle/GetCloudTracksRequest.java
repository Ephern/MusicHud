package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.user.cloud.CloudTracksPage;
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
public class GetCloudTracksRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetCloudTracksRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.INT, GetCloudTracksRequest::getOffset,
                    Codecs.INT, GetCloudTracksRequest::getLimit,
                    GetCloudTracksRequest::new
            )
    );

    private final int offset;
    private final int limit;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetCloudTracksRequest.class, CODEC, (request, player) -> {
                try {
                    CloudTracksPage page = IMusicApiService.getInstance(ApiProvider.NCM)
                            .getUserCloudTracks(request.offset, request.limit, player.getUUID());
                    return ResponseResult.of(new GetCloudTracksResponse(
                            MessagedResult.success(page)));
                } catch (Throwable e) {
                    return ResponseResult.of(new GetCloudTracksResponse(
                            MessagedResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage(), CloudTracksPage.EMPTY)));
                }
            });
        }
    }
}
