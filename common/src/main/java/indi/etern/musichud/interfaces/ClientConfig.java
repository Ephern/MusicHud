package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.PlatformServiceRegistry;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;

public interface ClientConfig {
    void setEnable(boolean enable);
    void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics);
    void setDisableVanillaMusic(boolean disableVanillaMusic);
    void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying);
    void setEnableHud(boolean enableHud);
    void setPrimaryChosenQuality(Quality primaryChosenQuality);
    void setHudVerticalPosition(VerticalAlign hudVerticalPosition);
    void setHudHorizontalPosition(HorizontalAlign hudHorizontalPosition);
    void setHudOffsetX(int hudOffsetX);
    void setHudOffsetY(int hudOffsetY);
    void setHudWidth(int hudWidth);
    void setHudHeight(int hudHeight);
    void setHudCornerRadius(int hudCornerRadius);
    void setClientCookie(LoginCookieInfo clientCookie);
    void setClientAccountConfig(ProfileConfigData clientAccountConfig);
    void setEnableEmbeddedServer(boolean enableEmbeddedServer);

    boolean getEnable();
    boolean getShowTranslatedCnLyrics();
    boolean getDisableVanillaMusic();
    boolean getHideHudWhenNotPlaying();
    boolean getEnableHud();
    Quality getPrimaryChosenQuality();
    VerticalAlign getHudVerticalPosition();
    HorizontalAlign getHudHorizontalPosition();
    int getHudOffsetX();
    int getHudOffsetY();
    int getHudWidth();
    int getHudHeight();
    int getHudCornerRadius();
    LoginCookieInfo getClientCookie();
    ProfileConfigData getClientAccountConfig();
    boolean getEnableEmbeddedServer();

    void save();

    void setConfigured(boolean configured);
    boolean isConfigured();

    static ClientConfig getInstance() {
        ClientConfig registered = PlatformServiceRegistry.getClientConfig();
        if (registered != null) {
            return registered;
        }
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ClientConfigDefinition.getInstance();
            }
        }
        throw new UnsupportedOperationException();
    }
}
