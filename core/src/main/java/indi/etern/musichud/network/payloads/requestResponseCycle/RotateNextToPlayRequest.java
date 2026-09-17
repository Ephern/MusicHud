package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;

public class RotateNextToPlayRequest extends ApiRequestPayload {
    public static final ByteBufCodec<RotateNextToPlayRequest> CODEC =
            RequestResponseCodecs.withCycleId(ByteBufCodec.unit(RotateNextToPlayRequest::new));

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(RotateNextToPlayRequest.class, CODEC,
                    (request, playerClient) -> ResponseResult.of(new RotateNextToPlayResponse(
                            MusicPlayerServerService.getInstance().rotateNextToPlay(PusherInfo.ofPlayer(playerClient)))));
        }
    }
}
