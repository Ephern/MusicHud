package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.music.actions.ModifyType;
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

public record ModifyLikeListRequest(long musicId, ModifyType modifyType) implements C2SPayload {
    public static final ByteBufCodec<ModifyLikeListRequest> CODEC =
            ByteBufCodec.composite(
                    Codecs.LONG,
                    ModifyLikeListRequest::musicId,
                    Codecs.ofEnum(ModifyType.class),
                    ModifyLikeListRequest::modifyType,
                    ModifyLikeListRequest::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(ModifyLikeListRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, playerClient) -> {
                        IMusicApiService instance = IMusicApiService.getInstance(ApiProvider.NCM);
                        try {
                            if (request.modifyType == ModifyType.ADD) {
                                instance.addToLikedList(request.musicId, playerClient.getUUID());
                            } else {
                                instance.removeFromLikedList(request.musicId, playerClient.getUUID());
                            }
                            IServerNetworkService.getInstance().sendToPlayer(playerClient, new ModifyLikeListResponse(MessagedResult.success(request)));
                        } catch (Throwable e) {
                            IServerNetworkService.getInstance().sendToPlayer(playerClient, new ModifyLikeListResponse(MessagedResult.fail(e.getMessage(), request)));
                        }
                    })
            );
        }
    }
}
