package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.actions.MessagedResult;
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
public class GetUserRecentAlbumRecordResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetUserRecentAlbumRecordResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.ofList(() -> PlayRecord.codec(() -> Album.CODEC))),
                    GetUserRecentAlbumRecordResponse::getResult,
                    GetUserRecentAlbumRecordResponse::new
            )
    );

    private final MessagedResult<List<PlayRecord<Album>>> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetUserRecentAlbumRecordResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
