package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
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
public class SubscribeRequest extends ApiRequestPayload {
    public static final ByteBufCodec<SubscribeRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    SubscribeRequest::getId,
                    Codecs.ofEnum(SubscribableType.class),
                    SubscribeRequest::getSubscribableType,
                    Codecs.ofEnum(SubscribeAction.class),
                    SubscribeRequest::getAction,
                    SubscribeRequest::new
            )
    );

    private final long id;
    private final SubscribableType subscribableType;
    private final SubscribeAction action;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(SubscribeRequest.class, CODEC, (request, playerClient) -> {
                IMusicApiService instance = IMusicApiService.getInstance(ApiProvider.NCM);
                try {
                    instance.userSubscribe(request.getId(), request.getSubscribableType(), request.getAction(), playerClient.getUUID());
                    return ResponseResult.of(new ModifyPlaylistResponse(true, ""));
                } catch (Throwable e) {
                    return ResponseResult.of(new ModifyPlaylistResponse(false, e.getMessage()));
                }
            });
        }
    }
}
