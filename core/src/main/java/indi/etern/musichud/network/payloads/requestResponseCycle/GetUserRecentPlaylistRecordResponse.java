package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.record.PlayRecord;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetUserRecentPlaylistRecordResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetUserRecentPlaylistRecordResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.ofList(() -> PlayRecord.codec(() -> Playlist.CODEC))),
                    GetUserRecentPlaylistRecordResponse::getResult,
                    GetUserRecentPlaylistRecordResponse::new
            )
    );

    private final MessagedResult<List<PlayRecord<Playlist>>> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserRecentPlaylistRecordResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
