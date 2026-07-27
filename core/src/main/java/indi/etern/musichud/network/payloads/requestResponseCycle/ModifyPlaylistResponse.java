package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

public record ModifyPlaylistResponse(MessagedResult<ModifyPlaylistRequest> result) implements S2CPayload {
    public static final ByteBufCodec<ModifyPlaylistResponse> CODEC =
            ByteBufCodec.composite(
                    MessagedResult.codec(ModifyPlaylistRequest.CODEC),
                    ModifyPlaylistResponse::result,
                    ModifyPlaylistResponse::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(ModifyPlaylistResponse.class, CODEC,
                    (request, playerClient) -> {
                        //TODO
                    }
            );
        }
    }
}
