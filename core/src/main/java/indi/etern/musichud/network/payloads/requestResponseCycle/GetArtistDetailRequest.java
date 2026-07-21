package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
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

public record GetArtistDetailRequest(long id) implements C2SPayload {
    public static final ByteBufCodec<GetArtistDetailRequest> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            GetArtistDetailRequest::id,
            GetArtistDetailRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetArtistDetailRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        Artist artistDetail = IMusicApiService.getInstance(ApiProvider.NCM).getArtistDetail(playlistDetailRequest.id, player.getUUID());
                        if (artistDetail != null) {
                            IServerNetworkService.getInstance().sendToPlayer(player,new indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistDetailResponse(artistDetail));
                        }
                    })
            );
        }
    }
}
