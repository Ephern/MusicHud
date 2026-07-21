package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.List;
import java.util.function.Consumer;

public record GetUserAlbumsResponse(List<Album> albums) implements S2CPayload {
    public static final ByteBufCodec<GetUserAlbumsResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.ofList(() -> Album.CODEC),
                    GetUserAlbumsResponse::albums,
                    GetUserAlbumsResponse::new
            );

    static Consumer<List<Album>> consumer;

    public static void setConsumer(Consumer<List<Album>> consumer) {
        GetUserAlbumsResponse.consumer = consumer;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserAlbumsResponse.class, CODEC,
                    (payload, player) -> {
                        if (consumer != null) {
                            consumer.accept(payload.albums);
                        }
                    }
            );
        }
    }
}
