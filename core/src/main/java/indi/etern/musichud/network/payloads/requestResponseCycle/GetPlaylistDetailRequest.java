package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
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

public record GetPlaylistDetailRequest(long id, boolean ignoreCache) implements C2SPayload {
    public static final ByteBufCodec<GetPlaylistDetailRequest> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            GetPlaylistDetailRequest::id,
            Codecs.BOOL,
            GetPlaylistDetailRequest::ignoreCache,
            GetPlaylistDetailRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetPlaylistDetailRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        Playlist playlistDetail = IMusicApiService.getInstance(ApiProvider.NCM).getPlaylistDetail(playlistDetailRequest.id, playlistDetailRequest.ignoreCache, player.getUUID());
                        if (playlistDetail != null) {
                            IServerNetworkService.getInstance().sendToPlayer(player,new GetPlaylistDetailResponse(playlistDetail));
                        }
                    })
            );
        }
    }
}
