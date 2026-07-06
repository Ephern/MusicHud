package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Artist;
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
public class GetUserArtistsRequest implements C2SPayload {
    public static final GetUserArtistsRequest REQUEST = new GetUserArtistsRequest();
    public static final ByteBufCodec<GetUserArtistsRequest> CODEC = ByteBufCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserArtistsRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, player) -> {
                        List<Artist> playersUserArtists = IMusicApiService.getInstance(ApiProvider.NCM).getPlayersUserSubscribedArtists(player.getUUID());
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetUserArtistsResponse(playersUserArtists));
                    })
            );
        }
    }
}