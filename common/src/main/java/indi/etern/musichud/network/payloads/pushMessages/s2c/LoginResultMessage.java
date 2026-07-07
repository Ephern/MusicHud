package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

public record LoginResultMessage(boolean success, String message, LoginCookieInfo loginCookieInfo, Profile profile) implements S2CPayload {
    public static final ByteBufCodec<LoginResultMessage> CODEC =
            ByteBufCodec.composite(
                    Codecs.BOOL,
                    LoginResultMessage::success,
                    Codecs.STRING_UTF8,
                    LoginResultMessage::message,
                    LoginCookieInfo.STREAM_CODEC,
                    LoginResultMessage::loginCookieInfo,
                    Profile.CODEC,
                    LoginResultMessage::profile,
                    LoginResultMessage::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkReceiver<LoginResultMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = LoginService.getInstance().getLoginResultReceiver();
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    LoginResultMessage.class, CODEC,
                    receiver
            );
        }
    }
}
