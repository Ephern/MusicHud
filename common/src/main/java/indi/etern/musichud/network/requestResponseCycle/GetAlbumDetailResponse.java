package indi.etern.musichud.network.requestResponseCycle;

import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GetAlbumDetailResponse(AlbumInfo albumInfo) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, GetAlbumDetailResponse> CODEC =
            StreamCodec.composite(
                    AlbumInfo.CODEC,
                    GetAlbumDetailResponse::albumInfo,
                    GetAlbumDetailResponse::new
            );

    static Map<Long, Consumer<AlbumInfo>> consumerMap = new HashMap<>();
    public static void setReceiver(long id, Consumer<AlbumInfo> consumer) {
        if (consumerMap.containsKey(id)) {
            consumerMap.get(id).accept(null);
        }
        GetAlbumDetailResponse.consumerMap.put(id, consumer);
    }

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(
                    GetAlbumDetailResponse.class, CODEC,
                    (response, context) -> {
                        Consumer<AlbumInfo> consumer = consumerMap.remove(response.albumInfo.getId());
                        if (consumer != null) {
                            consumer.accept(response.albumInfo);
                        }
                    }
            );
        }
    }
}
