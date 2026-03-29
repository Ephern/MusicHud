package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.server.api.MusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GetUserPlaylistRequest implements C2SPayload {
    public static final GetUserPlaylistRequest REQUEST = new GetUserPlaylistRequest();
    public static final StreamCodec<RegistryFriendlyByteBuf, GetUserPlaylistRequest> CODEC = StreamCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserPlaylistRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, serverPlayer) -> {
                        List<Playlist> playersUserPlaylists = MusicApiService.getInstance().getPlayersUserSubscribedPlaylists(serverPlayer);
                        IServerNetworkService.getInstance().sendToPlayer(serverPlayer, new GetUserPlaylistResponse(playersUserPlaylists));
                    })
            );
        }
    }
}