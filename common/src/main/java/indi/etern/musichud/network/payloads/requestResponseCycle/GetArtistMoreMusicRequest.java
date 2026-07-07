package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
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

public record GetArtistMoreMusicRequest(long id, int offset) implements C2SPayload {
    public static final ByteBufCodec<GetArtistMoreMusicRequest> CODEC = ByteBufCodec.composite(
            Codecs.VAR_LONG,//TODO replace with Codecs.LONG in 1.3.0
            GetArtistMoreMusicRequest::id,
            Codecs.INT,
            GetArtistMoreMusicRequest::offset,
            GetArtistMoreMusicRequest::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetArtistMoreMusicRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((playlistDetailRequest, player) -> {
                        List<MusicDetail> musicDetails = IMusicApiService.getInstance(ApiProvider.NCM).getArtistMoreMusic(playlistDetailRequest.id, playlistDetailRequest.offset, player.getUUID());
                        if (musicDetails != null) {
                            IServerNetworkService.getInstance().sendToPlayer(player,new GetArtistMoreMusicResponse(playlistDetailRequest.id, playlistDetailRequest.offset, musicDetails));
                        }
                    })
            );
        }
    }
}
