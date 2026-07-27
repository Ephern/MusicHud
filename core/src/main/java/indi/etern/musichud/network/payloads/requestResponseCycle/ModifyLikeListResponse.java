package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

public record ModifyLikeListResponse(MessagedResult<ModifyLikeListRequest> result) implements S2CPayload {
    public static final ByteBufCodec<ModifyLikeListResponse> CODEC =
            ByteBufCodec.composite(
                    MessagedResult.codec(ModifyLikeListRequest.CODEC),
                    ModifyLikeListResponse::result,
                    ModifyLikeListResponse::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(ModifyLikeListResponse.class, CODEC,
                    (request, playerClient) -> {
                        //TODO
                    }
            );
        }
    }
}
