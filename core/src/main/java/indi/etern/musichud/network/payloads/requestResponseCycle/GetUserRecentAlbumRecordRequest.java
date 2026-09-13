package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.record.PlayRecord;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GetUserRecentAlbumRecordRequest extends ApiRequestPayload {
    public static final GetUserRecentAlbumRecordRequest REQUEST = new GetUserRecentAlbumRecordRequest();
    public static final ByteBufCodec<GetUserRecentAlbumRecordRequest> CODEC = RequestResponseCodecs.withCycleId(ByteBufCodec.unit(REQUEST));

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GetUserRecentAlbumRecordRequest.class, CODEC, (request, player) -> {
                try {
                    //noinspection unchecked
                    List<PlayRecord<Album>> result = (List<PlayRecord<Album>>) IMusicApiService.getInstance(ApiProvider.NCM)
                            .getUserPlayRecords(PlayRecord.ResourceType.ALBUM, player.getUUID());
                    return ResponseResult.of(new GetUserRecentAlbumRecordResponse(MessagedResult.success(result)));
                } catch (Exception e) {
                    return ResponseResult.of(new GetUserRecentAlbumRecordResponse(MessagedResult.fail(e.getMessage(), List.of())));
                }
            });
        }
    }
}
