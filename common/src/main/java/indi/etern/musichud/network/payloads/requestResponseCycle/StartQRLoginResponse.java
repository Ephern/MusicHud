package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record StartQRLoginResponse(String base64QRImg) implements S2CPayload {
    public static final StreamCodec<ByteBuf, StartQRLoginResponse> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    StartQRLoginResponse::base64QRImg,
                    StartQRLoginResponse::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    StartQRLoginResponse.class, CODEC,
                    LoginService.getInstance().getQrLoginResponseReceiver()
            );
        }
    }
}
