package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.record.PlayRecord;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetUserRecentTrackRecordRequest extends ApiRequestPayload {
    public static final GetUserRecentTrackRecordRequest REQUEST = new GetUserRecentTrackRecordRequest();
    public static final ByteBufCodec<GetUserRecentTrackRecordRequest> CODEC = RequestResponseCodecs.withCycleId(ByteBufCodec.unit(REQUEST));

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserRecentTrackRecordRequest.class, CODEC, (request, player) -> {
                try {
                    //noinspection unchecked
                    List<PlayRecord<MusicDetail>> result = (List<PlayRecord<MusicDetail>>) IMusicApiService.getInstance(ApiProvider.NCM)
                            .getUserPlayRecords(PlayRecord.ResourceType.SONG, player.getUUID());
                    return ResponseResult.of(new GetUserRecentTrackRecordResponse(MessagedResult.success(result)));
                } catch (Exception e) {
                    return ResponseResult.of(new GetUserRecentTrackRecordResponse(MessagedResult.fail(e.getMessage(), List.of())));
                }
            });
        }
    }
}
