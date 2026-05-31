package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SendPhoneValidationCodeRequest(int regionCode, long phone) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SendPhoneValidationCodeRequest> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SendPhoneValidationCodeRequest::regionCode,
                    ByteBufCodecs.LONG,
                    SendPhoneValidationCodeRequest::phone,
                    SendPhoneValidationCodeRequest::new
            );

    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SendPhoneValidationCodeRequest.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((request, player) -> {
                        ILoginApiService.getInstance(ApiProvider.NCM).requestValidationCodeFor(request.regionCode, request.phone, player);
                    })
            );
        }
    }
}
