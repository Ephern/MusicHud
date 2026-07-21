package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

public record StartQRLoginResponse(String base64QRImg) implements S2CPayload {
    public static final ByteBufCodec<StartQRLoginResponse> CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8,
                    StartQRLoginResponse::base64QRImg,
                    StartQRLoginResponse::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkReceiver<StartQRLoginResponse> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = IClientLoginService.getInstance().getQrLoginResponseReceiver();
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    StartQRLoginResponse.class, CODEC,
                    receiver
            );
        }
    }
}
