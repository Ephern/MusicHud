package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Consumer;

public record SendPhoneValidationCodeResponse(boolean success, int timeout) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SendPhoneValidationCodeResponse> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    SendPhoneValidationCodeResponse::success,
                    ByteBufCodecs.INT,
                    SendPhoneValidationCodeResponse::timeout,
                    SendPhoneValidationCodeResponse::new
            );

    static Consumer<SendPhoneValidationCodeResponse> consumer;

    public static void setReceiver(Consumer<SendPhoneValidationCodeResponse> receiver) {
        if (consumer != null) {
            consumer.accept(null);
        }
        consumer = receiver;
    }

    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SendPhoneValidationCodeResponse.class, CODEC,
                    (response, player) -> {
                        if (consumer != null) {
                            consumer.accept(response);
                        }
                    }
            );
        }
    }
}
