package indi.etern.musichud.network.payloads.requestResponseCycle;

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

@Getter
@AllArgsConstructor
public class DeleteCloudTrackRequest extends ApiRequestPayload {
    public static final ByteBufCodec<DeleteCloudTrackRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG, DeleteCloudTrackRequest::getId,
                    DeleteCloudTrackRequest::new
            )
    );

    private final long id;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(DeleteCloudTrackRequest.class, CODEC, (request, player) -> {
                try {
                    IMusicApiService.getInstance(ApiProvider.NCM)
                            .deleteCloudTracks(List.of(request.id), player.getUUID());
                    return ResponseResult.of(new DeleteCloudTrackResponse(MessagedResult.success(null)));
                } catch (Throwable e) {
                    return ResponseResult.of(new DeleteCloudTrackResponse(
                            MessagedResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage(), null)));
                }
            });
        }
    }
}
