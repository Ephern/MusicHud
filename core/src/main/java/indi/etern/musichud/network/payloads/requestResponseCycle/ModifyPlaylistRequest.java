package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.ModifyType;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.RequestHandlerRegistry;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.ResponseResult;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.network.payloads.pushMessages.s2c.CollectionUpdatedMessage;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ModifyPlaylistRequest extends ApiRequestPayload {
    public static final ByteBufCodec<ModifyPlaylistRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.LONG,
                    ModifyPlaylistRequest::getMusicId,
                    Codecs.LONG,
                    ModifyPlaylistRequest::getPlaylistId,
                    Codecs.ofEnum(ModifyType.class),
                    ModifyPlaylistRequest::getModifyType,
                    ModifyPlaylistRequest::new
            )
    );

    private final long musicId;
    private final long playlistId;
    private final ModifyType modifyType;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(ModifyPlaylistRequest.class, CODEC, (request, playerClient) -> {
                IMusicApiService instance = IMusicApiService.getInstance(ApiProvider.NCM);
                try {
                    if (request.getModifyType() == ModifyType.ADD) {
                        instance.addToPlaylist(request.getPlaylistId(), request.getMusicId(), playerClient.getUUID());
                    } else {
                        instance.removeFromPlaylist(request.getPlaylistId(), request.getMusicId(), playerClient.getUUID());
                    }
                } catch (Throwable e) {
                    return ResponseResult.of(new ModifyPlaylistResponse(false, e.getMessage()));
                }
                IServerNetworkService.getInstance().sendToPlayerInfos(
                        LoginApiService.getInstance().getPlayerInfoMap().values(),
                        new CollectionUpdatedMessage(request.getPlaylistId(), false)
                );
                return ResponseResult.of(new ModifyPlaylistResponse(true, ""));
            });
        }
    }
}
