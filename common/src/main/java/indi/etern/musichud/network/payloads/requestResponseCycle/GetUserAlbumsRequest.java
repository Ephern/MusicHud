package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GetUserAlbumsRequest implements C2SPayload {
    public static final GetUserAlbumsRequest REQUEST = new GetUserAlbumsRequest();
    public static final StreamCodec<RegistryFriendlyByteBuf, GetUserAlbumsRequest> CODEC = StreamCodec.unit(REQUEST);

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserAlbumsRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((getUserPlaylistRequest, player) -> {
                        List<Album> playersUserAlbums = IMusicApiService.getInstance(ApiProvider.NCM).getPlayersUserSubscribedAlbums(player);
                        IServerNetworkService.getInstance().sendToPlayer(player, new GetUserAlbumsResponse(playersUserAlbums));
                    })
            );
        }
    }
}