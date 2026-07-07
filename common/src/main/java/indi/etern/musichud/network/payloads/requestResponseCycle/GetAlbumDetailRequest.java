package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record GetAlbumDetailRequest(long id) implements C2SPayload {
    public static final ByteBufCodec<GetAlbumDetailRequest> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            GetAlbumDetailRequest::id,
            GetAlbumDetailRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetAlbumDetailRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        Album album = IMusicApiService.getInstance(ApiProvider.NCM).getAlbumInfoDetail(playlistDetailRequest.id, player.getUUID());
                        if (album != null) {
                            IServerNetworkService.getInstance().sendToPlayer(player,new GetAlbumDetailResponse(album));
                        }
                    })
            );
        }
    }
}
