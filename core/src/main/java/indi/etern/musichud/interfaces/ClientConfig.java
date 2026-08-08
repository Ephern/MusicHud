package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.user.ProfileConfigData;
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

    boolean getDefaultEnable();

    boolean getShowTranslatedCnLyrics();

    boolean getDefaultShowTranslatedCnLyrics();

    void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics);

    boolean getDisableVanillaMusic();

    void setDisableVanillaMusic(boolean disableVanillaMusic);

    boolean getDefaultDisableVanillaMusic();

    boolean getHideHudWhenNotPlaying();

    void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying);

    boolean getDefaultHideHudWhenNotPlaying();

    boolean getEnableHud();

    boolean getDefaultEnableHud();

    void setEnableHud(boolean enableHud);

    Quality getPrimaryChosenQuality();

    Quality getDefaultPrimaryChosenQuality();

    void setPrimaryChosenQuality(Quality primaryChosenQuality);

    String getHudVerticalPosition();

    String getDefaultHudVerticalPosition();

    void setHudVerticalPosition(String hudVerticalPosition);

    boolean getMixWithVanillaSoundVolume();

    boolean getDefaultMixWithVanillaSoundVolume();

    void setMixWithVanillaSoundVolume(boolean mixWithVanillaSoundVolume);

    boolean getMuted();

    void setMuted(boolean muted);

    int getSoundVolume();

    int getDefaultSoundVolume();

    void setSoundVolume(int soundVolume);

    void forceSetSoundVolume(int soundVolume);

    int getSoundVolumeInterval();

    int getDefaultSoundVolumeInterval();

    void setSoundVolumeInterval(int soundVolumeInterval);

    String getHudHorizontalPosition();

    String getDefaultHudHorizontalPosition();

    void setHudHorizontalPosition(String hudHorizontalPosition);

    int getHudOffsetX();

    int getDefaultHudOffsetX();

    void setHudOffsetX(int hudOffsetX);

    int getHudOffsetY();

    int getDefaultHudOffsetY();

    void setHudOffsetY(int hudOffsetY);

    int getHudWidth();

    int getDefaultHudWidth();

    void setHudWidth(int hudWidth);

    int getHudHeight();

    int getDefaultHudHeight();

    void setHudHeight(int hudHeight);

    int getHudCornerRadius();

    int getDefaultHudCornerRadius();

    void setHudCornerRadius(int hudCornerRadius);

    LoginCookieInfo getClientCookie();

    void setClientCookie(LoginCookieInfo clientCookie);

    ProfileConfigData getClientAccountConfig();

    void setClientAccountConfig(ProfileConfigData clientAccountConfig);

    boolean getEnabledInIntegratedServer();

    boolean getDefaultEnabledInIntegratedServer();

    void setEnabledInIntegratedServer(boolean enabledInIntegratedServer);

    boolean getEnableAutoConnect();

    boolean getDefaultEnableAutoConnect();

    void setEnableAutoConnect(boolean autoConnect);

    boolean getEnableIsolatedMode();

    boolean getDefaultEnableIsolatedMode();

    void setEnableIsolatedMode(boolean autoConnect);

    AutoConnectServerFilterType getConnectServerFilterType();

    AutoConnectServerFilterType getDefaultConnectServerFilterType();

    void setConnectServerFilterType(AutoConnectServerFilterType autoConnectServerFilterType);

    List<String> getBlackList();

    List<String> getDefaultBlackList();

    void setBlackList(List<String> blackList);

    List<String> getWhiteList();

    List<String> getDefaultWhiteList();

    void setWhiteList(List<String> whiteList);

    double getMainScreenAdditionalBackgroundDarken();

    double getDefaultMainScreenAdditionalBackgroundDarken();

    void setMainScreenAdditionalBackgroundDarken(double additionalBackgroundDarken);

    double getHudBackgroundMixAlpha();

    double getDefaultHudBackgroundMixAlpha();

    void setHudBackgroundMixAlpha(double hudBackgroundMixAlpha);

    boolean getEnableMarqueeText();

    boolean getDefaultEnableMarqueeText();

    void setEnableMarqueeText(boolean aBoolean);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);
}
