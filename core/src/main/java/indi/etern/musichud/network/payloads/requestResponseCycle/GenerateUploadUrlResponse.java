package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.user.cloud.UploadTaskMeta;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenerateUploadUrlResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GenerateUploadUrlResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(UploadTaskMeta.CODEC), GenerateUploadUrlResponse::getResult,
                    GenerateUploadUrlResponse::new
            )
    );
    MessagedResult<UploadTaskMeta> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GenerateUploadUrlResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
