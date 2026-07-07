package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.List;
import java.util.function.Consumer;

public record GetUserArtistsResponse(List<Artist> artists) implements S2CPayload {
    public static final ByteBufCodec<GetUserArtistsResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.ofList(() -> Artist.CODEC),
                    GetUserArtistsResponse::artists,
                    GetUserArtistsResponse::new
            );

    static Consumer<List<Artist>> consumer;

    public static void setConsumer(Consumer<List<Artist>> consumer) {
        GetUserArtistsResponse.consumer = consumer;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserArtistsResponse.class, CODEC,
                    (payload, player) -> {
                        if (consumer != null) {
                            consumer.accept(payload.artists);
                        }
                    }
            );
        }
    }
}
