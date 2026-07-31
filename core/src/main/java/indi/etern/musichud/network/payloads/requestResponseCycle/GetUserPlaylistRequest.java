package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.UserCategoryPlaylists;
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

public record GetUserPlaylistRequest(boolean ignoreCache) implements C2SPayload {
    public static final ByteBufCodec<GetUserPlaylistRequest> CODEC = ByteBufCodec.composite(
            Codecs.BOOL,
            GetUserPlaylistRequest::ignoreCache,
            GetUserPlaylistRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserPlaylistRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, player) -> {
                        UserCategoryPlaylists playersUserPlaylists = IMusicApiService.getInstance(ApiProvider.NCM)
                                .getPlayersUserPlaylists(getUserPlaylistRequest.ignoreCache, player.getUUID());
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetUserPlaylistResponse(playersUserPlaylists));
                    })
            );
        }
    }
}