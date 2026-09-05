package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.QueueItem;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
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
                            Traceable.codec(MusicDetail.CODEC),
                            GetInitialStateResponse::getCurrentPlaying,
                            Traceable.codec(MusicDetail.CODEC),
                            GetInitialStateResponse::getNextIdle,
                            Codecs.ZONED_DATE_TIME,
                            GetInitialStateResponse::getStartTime,
                            Codecs.ofQueue(() -> QueueItem.CODEC),
                            GetInitialStateResponse::getQueue,
                            Codecs.ofList(() -> IdlePlaySource.CODEC),
                            GetInitialStateResponse::getPlaylistSources,
                            GetInitialStateResponse::new
                    )
            );

    private final Traceable<MusicDetail> currentPlaying;
    private final Traceable<MusicDetail> nextIdle;
    private final ZonedDateTime startTime;
    private final Queue<QueueItem> queue;
    private final List<IdlePlaySource> playlistSources;

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
