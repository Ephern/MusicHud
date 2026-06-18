package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.platform.Environment;

import java.util.List;
import java.util.function.Supplier;

public interface ClientConfig {
    static ClientConfig getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<ClientConfig> supplier = platform.getClientConfigSupplier();
        if (supplier != null) {
            ClientConfig clientConfig = supplier.get();
            if (clientConfig != null) {
                return clientConfig;
            }
        }
        throw new UnsupportedOperationException();
    }

    boolean getEnable();

    void setEnable(boolean enable);

    boolean getShowTranslatedCnLyrics();

    void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics);

    boolean getDisableVanillaMusic();

    void setDisableVanillaMusic(boolean disableVanillaMusic);

    boolean getHideHudWhenNotPlaying();

    void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying);

    boolean getEnableHud();

    void setEnableHud(boolean enableHud);

    Quality getPrimaryChosenQuality();

    void setPrimaryChosenQuality(Quality primaryChosenQuality);

    VerticalAlign getHudVerticalPosition();

    void setHudVerticalPosition(VerticalAlign hudVerticalPosition);

    boolean getMixWithVanillaSoundVolume();

    void setMixWithVanillaSoundVolume(boolean mixWithVanillaSoundVolume);

    boolean getMuted();

    void setMuted(boolean muted);

    int getSoundVolume();

    void setSoundVolume(int soundVolume);

    void forceSetSoundVolume(int soundVolume);

    int getSoundVolumeInterval();

    void setSoundVolumeInterval(int soundVolumeInterval);

    HorizontalAlign getHudHorizontalPosition();

    void setHudHorizontalPosition(HorizontalAlign hudHorizontalPosition);

    int getHudOffsetX();

    void setHudOffsetX(int hudOffsetX);

    int getHudOffsetY();

    void setHudOffsetY(int hudOffsetY);

    int getHudWidth();

    void setHudWidth(int hudWidth);

    int getHudHeight();

    void setHudHeight(int hudHeight);

    int getHudCornerRadius();

    void setHudCornerRadius(int hudCornerRadius);

    LoginCookieInfo getClientCookie();

    void setClientCookie(LoginCookieInfo clientCookie);

    ProfileConfigData getClientAccountConfig();

    void setClientAccountConfig(ProfileConfigData clientAccountConfig);

    boolean getEnabledInIntegratedServer();

    void setEnabledInIntegratedServer(boolean enabledInIntegratedServer);

    boolean getEnableAutoConnect();

    void setEnableAutoConnect(boolean autoConnect);

    boolean getEnableIsolatedMode();

    void setEnableIsolatedMode(boolean autoConnect);

    AutoConnectServerFilterType getConnectServerFilterType();

    void setConnectServerFilterType(AutoConnectServerFilterType autoConnectServerFilterType);

    List<String> getBlackList();

    void setBlackList(List<String> blackList);

    List<String> getWhiteList();

    void setWhiteList(List<String> whiteList);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);

    double getMainScreenAdditionalBackgroundDarken();

    void setMainScreenAdditionalBackgroundDarken(double additionalBackgroundDarken);

    boolean getEnableMarqueeText();

    void setEnableMarqueeText(boolean aBoolean);
}
