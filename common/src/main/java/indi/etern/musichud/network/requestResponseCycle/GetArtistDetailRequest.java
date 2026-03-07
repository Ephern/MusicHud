package indi.etern.musichud.network.requestResponseCycle;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.C2SPayload;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.server.api.MusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GetArtistDetailRequest(long id) implements C2SPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, GetArtistDetailRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            GetArtistDetailRequest::id,
            GetArtistDetailRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(
                    GetArtistDetailRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        Artist artistDetail = MusicApiService.getInstance().getArtistDetail(playlistDetailRequest.id, player);
                        if (artistDetail != null) {
                            NetworkManager.sendToPlayer(player,new GetArtistDetailResponse(artistDetail));
                        }
                    })
            );
        }
    }
}
