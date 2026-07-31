package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.function.Consumer;

public record GetUserPlaylistResponse(UserCategoryPlaylists playlists) implements S2CPayload {
    public static final ByteBufCodec<GetUserPlaylistResponse> CODEC =
            ByteBufCodec.composite(
                    UserCategoryPlaylists.CODEC,
                    GetUserPlaylistResponse::playlists,
                    GetUserPlaylistResponse::new
            );

    static Consumer<UserCategoryPlaylists> consumer;

    public static void setConsumer(Consumer<UserCategoryPlaylists> consumer) {
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
