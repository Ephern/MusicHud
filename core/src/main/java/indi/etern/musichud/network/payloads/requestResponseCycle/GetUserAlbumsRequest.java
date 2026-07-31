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

import java.util.List;

public record GetUserAlbumsRequest(boolean ignoreCache) implements C2SPayload {
    public static final ByteBufCodec<GetUserAlbumsRequest> CODEC = ByteBufCodec.composite(
            Codecs.BOOL,
            GetUserAlbumsRequest::ignoreCache,
            GetUserAlbumsRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserAlbumsRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, player) -> {
                        List<Album> playersUserAlbums = IMusicApiService.getInstance(ApiProvider.NCM)
                                .getPlayersUserSubscribedAlbums(getUserPlaylistRequest.ignoreCache, player.getUUID());
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetUserAlbumsResponse(playersUserAlbums));
                    })
            );
        }
    }
}