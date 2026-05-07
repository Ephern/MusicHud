package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record GetArtistMoreMusicResponse(long artistId, int offset, List<MusicDetail> musicDetails) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetArtistMoreMusicResponse> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.LONG,
                    GetArtistMoreMusicResponse::artistId,
                    ByteBufCodecs.INT,
                    GetArtistMoreMusicResponse::offset,
                    Codecs.ofList(() -> MusicDetail.CODEC),
                    GetArtistMoreMusicResponse::musicDetails,
                    GetArtistMoreMusicResponse::new
            );

    public record RequestData(long artistId, int offset){}
    static final Map<RequestData, Consumer<List<MusicDetail>>> consumerMap = new HashMap<>();
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
