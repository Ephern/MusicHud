package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.List;
import java.util.function.Consumer;

public record GetUserPlaylistResponse(List<Playlist> playlists) implements S2CPayload {
    public static final ByteBufCodec<GetUserPlaylistResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.ofList(() -> Playlist.CODEC),
                    GetUserPlaylistResponse::playlists,
                    GetUserPlaylistResponse::new
            );

    static Consumer<List<Playlist>> consumer;

    public static void setConsumer(Consumer<List<Playlist>> consumer) {
        GetUserPlaylistResponse.consumer = consumer;
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserPlaylistResponse.class, CODEC,
                    (payload, player) -> {
                        if (consumer != null) {
                            consumer.accept(payload.playlists);
                        }
                    }
            );
        }
    }
}
