package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GetAlbumDetailResponse(Album album) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetAlbumDetailResponse> CODEC =
            StreamCodec.composite(
                    Album.CODEC,
                    GetAlbumDetailResponse::album,
                    GetAlbumDetailResponse::new
            );

    static Map<Long, Consumer<Album>> consumerMap = new HashMap<>();
    public static void setReceiver(long id, Consumer<Album> consumer) {
        if (consumerMap.containsKey(id)) {
            consumerMap.get(id).accept(null);
        }
        GetAlbumDetailResponse.consumerMap.put(id, consumer);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetAlbumDetailResponse.class, CODEC,
                    (response, player) -> {
                        Consumer<Album> consumer = consumerMap.remove(response.album.getId());
                        if (consumer != null) {
                            consumer.accept(response.album);
                        }
                    }
            );
        }
    }
}
