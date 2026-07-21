package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GetUserPlaylistRequest implements C2SPayload {
    public static final GetUserPlaylistRequest REQUEST = new GetUserPlaylistRequest();
    public static final ByteBufCodec<GetUserPlaylistRequest> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserPlaylistRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, player) -> {
                        List<Playlist> playersUserPlaylists = IMusicApiService.getInstance(ApiProvider.NCM).getPlayersUserSubscribedPlaylists(player.getUUID());
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetUserPlaylistResponse(playersUserPlaylists));
                    })
            );
        }
    }
}