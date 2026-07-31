package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GetPlaylistDetailResponse(Playlist playlist) implements S2CPayload {
    public static final ByteBufCodec<GetPlaylistDetailResponse> CODEC = ByteBufCodec.composite(
            Playlist.CODEC,
            GetPlaylistDetailResponse::playlist,
            GetPlaylistDetailResponse::new
    );

    static final Map<Long, Consumer<Playlist>> consumerMap = new ConcurrentHashMap<>();
    public static void setReceiver(long id, Consumer<Playlist> consumer) {
        if (consumerMap.containsKey(id)) {
            consumerMap.get(id).accept(null);
        }
        GetPlaylistDetailResponse.consumerMap.put(id, consumer);
    }

    public static void removeReceiver(long id) {
        consumerMap.remove(id);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetPlaylistDetailResponse.class, CODEC,
                    (playlistDetailResponse, player) -> {
                        Consumer<Playlist> consumer = consumerMap.remove(playlistDetailResponse.playlist().getId());
                        if (consumer != null) {
                            consumer.accept(playlistDetailResponse.playlist);
                        }
                    }
            );
        }
    }
}
