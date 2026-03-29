package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.server.api.MusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GetAlbumDetailRequest(long id) implements C2SPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, GetAlbumDetailRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            GetAlbumDetailRequest::id,
            GetAlbumDetailRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetAlbumDetailRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        AlbumInfo albumInfo = MusicApiService.getInstance().getAlbumInfoDetail(playlistDetailRequest.id, player);
                        if (albumInfo != null) {
                            IServerNetworkService.getInstance().sendToPlayer(player,new GetAlbumDetailResponse(albumInfo));
                        }
                    })
            );
        }
    }
}
