package indi.etern.musichud.beans.login;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.platform.Environment;
import org.apache.logging.log4j.Logger;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public record LoginCookieInfo(LoginType type, String rawCookie, ZonedDateTime generateTime) {
    private static final Logger logger = MusicHud.getLogger(LoginCookieInfo.class);
    public static final ByteBufCodec<LoginCookieInfo> STREAM_CODEC =
            ByteBufCodec.composite(
                    LoginType.PACKET_CODEC,
                    LoginCookieInfo::type,
                    Codecs.STRING_UTF8,
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
    private static ClientConfig clientConfig;
    static {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
            try {
                clientConfig = ClientConfig.getInstance();
            } catch (UnsupportedOperationException e) {
                clientConfig = null;
            }
        }
    }

    public static LoginCookieInfo clientCurrentCookie() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            if (clientConfig == null) {
                throw new IllegalStateException("\"clientConfig\" is null in client");
            }
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
