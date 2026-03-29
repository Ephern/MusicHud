package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LoginResultMessage(boolean success, String message, LoginCookieInfo loginCookieInfo, Profile profile) implements S2CPayload {
    public static final
    StreamCodec<RegistryFriendlyByteBuf, LoginResultMessage> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    LoginResultMessage::success,
                    ByteBufCodecs.STRING_UTF8,
                    LoginResultMessage::message,
                    LoginCookieInfo.STREAM_CODEC,
                    LoginResultMessage::loginCookieInfo,
                    Profile.STREAM_CODEC,
                    LoginResultMessage::profile,
                    LoginResultMessage::new
            );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    LoginResultMessage.class, CODEC,
                    LoginService.getInstance().getLoginResultReceiver()
            );
        }
    }
}
