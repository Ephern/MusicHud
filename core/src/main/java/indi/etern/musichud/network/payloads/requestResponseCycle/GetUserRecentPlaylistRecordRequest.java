package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.result.MessagedResult;
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
public class GetUserRecentPlaylistRecordRequest extends ApiRequestPayload {
    public static final GetUserRecentPlaylistRecordRequest REQUEST = new GetUserRecentPlaylistRecordRequest();
    public static final ByteBufCodec<GetUserRecentPlaylistRecordRequest> CODEC = RequestResponseCodecs.withCycleId(ByteBufCodec.unit(REQUEST));

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserRecentPlaylistRecordRequest.class, CODEC, (request, player) -> {
                try {
                    //noinspection unchecked
                    List<PlayRecord<Playlist>> result = (List<PlayRecord<Playlist>>) IMusicApiService.getInstance(ApiProvider.NCM)
                            .getUserPlayRecords(PlayRecord.ResourceType.PLAYLIST, player.getUUID());
                    return ResponseResult.of(new GetUserRecentPlaylistRecordResponse(MessagedResult.success(result)));
                } catch (Exception e) {
                    return ResponseResult.of(new GetUserRecentPlaylistRecordResponse(MessagedResult.fail(e.getMessage(), List.of())));
                }
            });
        }
    }
}
