package indi.etern.musichud.network.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.function.Consumer;

public record GetUserPlaylistResponse(List<Playlist> playlists) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetUserPlaylistResponse> CODEC =
            StreamCodec.composite(
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
            NetworkRegisterUtil.autoRegisterPayload(
                    GetUserPlaylistResponse.class, CODEC,
                    (payload, context) -> {
                        if (consumer != null) {
                            consumer.accept(payload.playlists);
                        }
                    }
            );
        }
    }
}
