package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;

public class GetInitialStateRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GetInitialStateRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    ByteBufCodec.unit(0),
                    ignored -> 0,
                    ignored -> new GetInitialStateRequest()
            )
    );

    public GetInitialStateRequest() {
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetInitialStateRequest.class, CODEC, (request, player) ->
                    ResponseResult.of(MusicPlayerServerService.getInstance().buildInitialStateFor(player))
            );
        }
    }
}
