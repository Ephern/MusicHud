package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.user.cloud.CloudTracksPage;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.RequestResponseCodecs;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetCloudTracksResponse extends ApiResponsePayload {
    public static final ByteBufCodec<GetCloudTracksResponse> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    MessagedResult.codec(CloudTracksPage.CODEC), GetCloudTracksResponse::getResult,
                    GetCloudTracksResponse::new
            )
    );

    private final MessagedResult<CloudTracksPage> result;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    GetCloudTracksResponse.class, CODEC,
                    (response, player) -> RequestResponseManager.complete(response)
            );
        }
    }
}
