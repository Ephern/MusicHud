package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record GetArtistMoreMusicResponse(long artistId, int offset, List<MusicDetail> musicDetails) implements S2CPayload {
    public static final ByteBufCodec<GetArtistMoreMusicResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.LONG,
                    GetArtistMoreMusicResponse::artistId,
                    Codecs.INT,
                    GetArtistMoreMusicResponse::offset,
                    Codecs.ofList(() -> MusicDetail.CODEC),
                    GetArtistMoreMusicResponse::musicDetails,
                    GetArtistMoreMusicResponse::new
            );

    public record RequestData(long artistId, int offset){}
    static final Map<RequestData, Consumer<List<MusicDetail>>> consumerMap = new ConcurrentHashMap<>();
    public static void setReceiver(RequestData requestData, Consumer<List<MusicDetail>> consumer) {
        if (consumerMap.containsKey(requestData)) {
            consumerMap.get(requestData).accept(null);
        }
        GetArtistMoreMusicResponse.consumerMap.put(requestData, consumer);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetArtistMoreMusicResponse.class, CODEC,
                    (response, player) -> {
                        Consumer<List<MusicDetail>> consumer = consumerMap.remove(new RequestData(response.artistId, response.offset));
                        if (consumer != null) {
                            consumer.accept(response.musicDetails);
                        }
                    }
            );
        }
    }
}
