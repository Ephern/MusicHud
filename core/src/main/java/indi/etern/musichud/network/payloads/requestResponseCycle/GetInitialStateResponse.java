package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Queue;

@Getter
@AllArgsConstructor
public class GetInitialStateResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetInitialStateResponse> CODEC =
            RequestResponseCodecs.withCycleId(
                    ByteBufCodec.composite(
                            MusicDetail.CODEC,
                            GetInitialStateResponse::getCurrentPlaying,
                            MusicDetail.CODEC,
                            GetInitialStateResponse::getNextIdle,
                            Codecs.ZONED_DATE_TIME,
                            GetInitialStateResponse::getStartTime,
                            Codecs.ofQueue(() -> MusicDetail.CODEC),
                            GetInitialStateResponse::getQueue,
                            Codecs.ofList(() -> Playlist.CODEC),
                            GetInitialStateResponse::getPlaylistSources,
                            Codecs.ofList(() -> Album.CODEC),
                            GetInitialStateResponse::getAlbumSources,
                            GetInitialStateResponse::new
                    )
            );

    private final MusicDetail currentPlaying;
    private final MusicDetail nextIdle;
    private final ZonedDateTime startTime;
    private final Queue<MusicDetail> queue;
    private final List<Playlist> playlistSources;
    private final List<Album> albumSources;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetInitialStateResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
