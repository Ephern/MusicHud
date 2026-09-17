package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompleteCloudUploadResponse extends ApiResponsePayload {
    public static final ByteBufCodec<CompleteCloudUploadResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(Codecs.STRING_UTF8), CompleteCloudUploadResponse::getResult,
                    CompleteCloudUploadResponse::new
            )
    );

    /** {@code extraData} is the resolved Netease cloud track id, or {@code ""} when unknown. */
    private final MessagedResult<String> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    CompleteCloudUploadResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
