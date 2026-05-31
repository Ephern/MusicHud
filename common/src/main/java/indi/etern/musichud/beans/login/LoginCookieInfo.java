package indi.etern.musichud.beans.login;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.platform.Environment;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.apache.logging.log4j.Logger;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public record LoginCookieInfo(LoginType type, String rawCookie, ZonedDateTime generateTime) {
    private static final Logger logger = MusicHud.getLogger(LoginCookieInfo.class);
    public static final StreamCodec<ByteBuf, LoginCookieInfo> STREAM_CODEC =
            StreamCodec.composite(
                    LoginType.PACKET_CODEC,
                    LoginCookieInfo::type,
                    ByteBufCodecs.STRING_UTF8,
                    LoginCookieInfo::rawCookie,
                    Codecs.ZONED_DATE_TIME,
                    LoginCookieInfo::generateTime,
                    LoginCookieInfo::new
            );
    public static final LoginCookieInfo UNLOGGED = new LoginCookieInfo(
            LoginType.UNLOGGED,
            "",
            ZonedDateTime.of(114514, 1, 9, 1, 9, 8, 10, ZoneId.systemDefault())
    );
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    public static LoginCookieInfo clientCurrentCookie() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            try {
                LoginCookieInfo loginCookieInfo = clientConfig.getClientCookie();
                if (loginCookieInfo == null) {
                    return UNLOGGED;
                }
                return loginCookieInfo;
            } catch (RuntimeException e) {
                return UNLOGGED;
            }
        } else {
            throw new IllegalStateException("Cannot invoke \"LoginCookieInfo.getClientCookie\" in server");
        }
    }

    public static void setClientCookie(LoginCookieInfo loginCookieInfo) {
        try {
            clientConfig.setClientCookie(loginCookieInfo);
            clientConfig.save();
            logger.info("Login cookie saved");
        } catch (RuntimeException e) {
            logger.error("Exception occurred when serializing login cookie and save", e);
        }
    }

    public void setToClientCookie() {
        LoginCookieInfo.setClientCookie(this);
    }
}
