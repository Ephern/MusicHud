package indi.etern.musichud.network.requestResponseCycle;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.C2SPayload;
import indi.etern.musichud.network.NetworkRegisterUtil;
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
            NetworkRegisterUtil.autoRegisterPayload(
                    GetUserPlaylistRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, serverPlayer) -> {
                        List<Playlist> playersUserPlaylists = MusicApiService.getInstance().getPlayersUserSubsctibedPlaylists(serverPlayer);
                        NetworkManager.sendToPlayer(serverPlayer, new GetUserPlaylistResponse(playersUserPlaylists));
                    })
            );
        }
    }
}