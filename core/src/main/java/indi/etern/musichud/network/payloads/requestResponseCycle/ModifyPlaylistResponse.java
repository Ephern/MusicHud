package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

public record ModifyPlaylistResponse(MessagedResult<ModifyPlaylistRequest> result) implements S2CPayload {
    public static final ByteBufCodec<ModifyPlaylistResponse> CODEC =
            ByteBufCodec.composite(
                    MessagedResult.codec(ModifyPlaylistRequest.CODEC),
                    ModifyPlaylistResponse::result,
                    ModifyPlaylistResponse::new
            );
    static final Map<ModifyPlaylistRequest, Consumer<MessagedResult<ModifyPlaylistRequest>>> consumerMap = new ConcurrentHashMap<>();
    public static void setReceiver(ModifyPlaylistRequest request, Consumer<MessagedResult<ModifyPlaylistRequest>> consumer) {
        if (consumerMap.containsKey(request)) {
            consumerMap.get(request).accept(null);
        }
        consumerMap.put(request, consumer);
    }


    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(ModifyPlaylistResponse.class, CODEC,
                    (response, playerClient) -> {
                        ModifyPlaylistRequest request = response.result.extraData();
                        Consumer<MessagedResult<ModifyPlaylistRequest>> remove = consumerMap.remove(request);
                        if (remove != null) {
                            remove.accept(response.result);
                        }
                    }
            );
        }
    }
}
