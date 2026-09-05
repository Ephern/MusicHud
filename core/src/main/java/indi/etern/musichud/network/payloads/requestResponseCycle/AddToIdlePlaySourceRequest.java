package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Add-idle-play-source request; the server validates and (for intelligent
 * sources) performs the initial fetch before replying.
 */
@Getter
@AllArgsConstructor
public class AddToIdlePlaySourceRequest extends ApiRequestPayload {
    public static final ByteBufCodec<AddToIdlePlaySourceRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    IdlePlaySource.CODEC,
                    AddToIdlePlaySourceRequest::getIdlePlaySource,
                    AddToIdlePlaySourceRequest::new
            )
    );

    private final IdlePlaySource idlePlaySource;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(AddToIdlePlaySourceRequest.class, CODEC, (request, playerClient) -> {
                var result = MusicPlayerServerService.getInstance()
                        .addIdlePlaySource(request.getIdlePlaySource(), PusherInfo.ofPlayer(playerClient));
                return ResponseResult.of(new AddToIdlePlaySourceResponse(result));
            });
        }
    }
}
