package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GetMusicResourceResponse(MusicResourceInfo musicResourceInfo) implements S2CPayload {
    public static final ByteBufCodec<GetMusicResourceResponse> CODEC =
            ByteBufCodec.composite(
                    MusicResourceInfo.CODEC,
                    GetMusicResourceResponse::musicResourceInfo,
                    GetMusicResourceResponse::new
            );

    static final Map<Long, Consumer<MusicResourceInfo>> consumerMap = new HashMap<>();
    public static void setReceiver(long id, Consumer<MusicResourceInfo> consumer) {
        if (consumerMap.containsKey(id)) {
            consumerMap.get(id).accept(null);
        }
        GetMusicResourceResponse.consumerMap.put(id, consumer);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetMusicResourceResponse.class, CODEC,
                    (response, player) -> {
                        Consumer<MusicResourceInfo> consumer = consumerMap.remove(response.musicResourceInfo.getId());
                        if (consumer != null) {
                            consumer.accept(response.musicResourceInfo);
                        }
                    }
            );
        }
    }
}
