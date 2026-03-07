package indi.etern.musichud.network.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GetArtistDetailResponse(Artist artist) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetArtistDetailResponse> CODEC =
            StreamCodec.composite(
                    Artist.CODEC,
                    GetArtistDetailResponse::artist,
                    GetArtistDetailResponse::new
            );

    static Map<Long, Consumer<Artist>> consumerMap = new HashMap<>();
    public static void setReceiver(long id, Consumer<Artist> consumer) {
        if (consumerMap.containsKey(id)) {
            consumerMap.get(id).accept(null);
        }
        GetArtistDetailResponse.consumerMap.put(id, consumer);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(
                    GetArtistDetailResponse.class, CODEC,
                    (response, context) -> {
                        Consumer<Artist> consumer = consumerMap.remove(response.artist.getId());
                        if (consumer != null) {
                            consumer.accept(response.artist);
                        }
                    }
            );
        }
    }
}
