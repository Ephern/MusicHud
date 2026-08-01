package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class SubscribeChannelMessage extends ApiRequestPayload {
    public static final ByteBufCodec<SubscribeChannelMessage> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.ofSet(() -> Codecs.STRING_UTF8),
                    SubscribeChannelMessage::getChannels,
                    SubscribeChannelMessage::new
            )
    );

    private final Set<String> channels;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(SubscribeChannelMessage.class, CODEC, (message, player) -> {
                ChannelManager.subscribe(player, message.getChannels());
                return ResponseResult.of(new SubscribeChannelResponse(message.getChannels()));
            });
        }
    }
}
