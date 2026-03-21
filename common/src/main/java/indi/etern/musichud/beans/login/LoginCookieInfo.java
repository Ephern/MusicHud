package indi.etern.musichud.beans.login;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.requestResponseCycle.CookieLoginRequest;
import indi.etern.musichud.utils.JsonUtil;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.apache.logging.log4j.Logger;

import java.time.Period;
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
    private static final Period refreshInterval = Period.of(0,0,1);
    public static LoginCookieInfo fromJson(String json) {
        try {
            LoginCookieInfo loginCookieInfo = JsonUtil.gson.fromJson(json, LoginCookieInfo.class);
            if (loginCookieInfo == null) {
                return UNLOGGED;
            }
            return loginCookieInfo;
        } catch (RuntimeException e) {
            return UNLOGGED;
        }
    }

    public static LoginCookieInfo clientCurrentCookie() {
        if (Platform.getEnv() == EnvType.CLIENT) {
            return fromJson(ClientConfigDefinition.clientCookie.get());
        } else {
            throw new IllegalStateException("Cannot invoke \"LoginCookieInfo.getClientCookie\" in server");
        }
    }

    public static void setClientCookie(LoginCookieInfo loginCookieInfo) {
        try {
            ClientConfigDefinition.clientCookie.set(JsonUtil.gson.toJson(loginCookieInfo));
            ClientConfigDefinition.clientCookie.save();
            logger.info("Login cookie saved");
        } catch (RuntimeException e) {
            logger.error("Exception occurred when serializing login cookie and save", e);
        }
    }

    public static void refreshIfNecessaryAndRegisterToServer() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        if (loginCookieInfo.generateTime.plus(refreshInterval).isBefore(ZonedDateTime.now())) {
            logger.info("Refreshing Login Cookie");
            NetworkManager.sendToServer(new CookieLoginRequest(loginCookieInfo, true));
        } else {
            NetworkManager.sendToServer(new CookieLoginRequest(loginCookieInfo, false));
        }
    }

    public void setToClientCookie() {
        LoginCookieInfo.setClientCookie(this);
    }
}