package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.beans.option.ScrobbleOption;
import indi.etern.musichud.beans.option.MultichannelMode;
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

    boolean getEnableLyricBlur();

    boolean getDefaultEnableLyricBlur();

    void setEnableLyricBlur(boolean enableLyricBlur);

    boolean getDisableVanillaMusic();

    void setDisableVanillaMusic(boolean disableVanillaMusic);

    boolean getDefaultDisableVanillaMusic();

    boolean getHideHudWhenNotPlaying();

    void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying);

    boolean getDefaultHideHudWhenNotPlaying();

    boolean getEnableHud();

    boolean getDefaultEnableHud();

    void setEnableHud(boolean enableHud);

    boolean getEnableLyricsSidebar();

    boolean getDefaultEnableLyricsSidebar();

    void setEnableLyricsSidebar(boolean enableLyricsSidebar);

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

    boolean getEnableGuiFluidBackground();

    boolean getDefaultEnableGuiFluidBackground();

    void setEnableGuiFluidBackground(boolean enableGuiFluidBackground);

    double getFluidBackgroundAlpha();

    double getDefaultFluidBackgroundAlpha();

    void setFluidBackgroundAlpha(double fluidBackgroundAlpha);

    double getFluidBackgroundBrightness();

    double getDefaultFluidBackgroundBrightness();

    void setFluidBackgroundBrightness(double fluidBackgroundBrightness);

    double getSolidBackgroundAlpha();

    double getDefaultSolidBackgroundAlpha();

    void setSolidBackgroundAlpha(double solidBackgroundAlpha);

    double getSolidBackgroundBrightness();

    double getDefaultSolidBackgroundBrightness();

    void setSolidBackgroundBrightness(double solidBackgroundBrightness);

    double getHudBackgroundMixAlpha();

    double getDefaultHudBackgroundMixAlpha();

    void setHudBackgroundMixAlpha(double hudBackgroundMixAlpha);

    boolean getEnableMarqueeText();

    boolean getDefaultEnableMarqueeText();

    void setEnableMarqueeText(boolean aBoolean);

    boolean getShowHudLayoutBoxes();

    boolean getDefaultShowHudLayoutBoxes();

    void setShowHudLayoutBoxes(boolean showHudLayoutBoxes);

    boolean getShowThemeColor();

    boolean getDefaultShowThemeColor();

    void setShowThemeColor(boolean showThemeColor);

    boolean getForceGL32Path();

    boolean getDefaultForceGL32Path();

    void setForceGL32Path(boolean force);

    boolean getForceRgba8Fallback();

    boolean getDefaultForceRgba8Fallback();

    void setForceRgba8Fallback(boolean force);

    int getGuiBackgroundBlurRadius();

    int getDefaultGuiBackgroundBlurRadius();

    void setGuiBackgroundBlurRadius(int radius);

    double getGuiBackgroundCoverOpacity();

    double getDefaultGuiBackgroundCoverOpacity();

    void setGuiBackgroundCoverOpacity(double opacity);

    double getGuiBackgroundCoverLayerOpacity();

    double getDefaultGuiBackgroundCoverLayerOpacity();

    void setGuiBackgroundCoverLayerOpacity(double opacity);

    double getGuiBackgroundSaturation();

    double getDefaultGuiBackgroundSaturation();

    void setGuiBackgroundSaturation(double saturation);

    int getGuiBackgroundFluidBlurRadius();

    int getDefaultGuiBackgroundFluidBlurRadius();

    void setGuiBackgroundFluidBlurRadius(int radius);

    int getGuiBackgroundCoverRotationSpeed();

    int getDefaultGuiBackgroundCoverRotationSpeed();

    void setGuiBackgroundCoverRotationSpeed(int degreesPerSecond);

    double getGuiBackgroundCoverDriftX();

    double getDefaultGuiBackgroundCoverDriftX();

    void setGuiBackgroundCoverDriftX(double amplitude);

    double getGuiBackgroundCoverDriftY();

    double getDefaultGuiBackgroundCoverDriftY();

    void setGuiBackgroundCoverDriftY(double amplitude);

    double getGuiBackgroundCoverDriftSpeed();

    double getDefaultGuiBackgroundCoverDriftSpeed();

    void setGuiBackgroundCoverDriftSpeed(double speed);

    double getGuiBackgroundCoverOrbitDriftX();

    double getDefaultGuiBackgroundCoverOrbitDriftX();

    void setGuiBackgroundCoverOrbitDriftX(double amplitude);

    double getGuiBackgroundCoverOrbitDriftY();

    double getDefaultGuiBackgroundCoverOrbitDriftY();

    void setGuiBackgroundCoverOrbitDriftY(double amplitude);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);

    ScrobbleOption getScrobbleOption();

    ScrobbleOption getDefaultScrobbleOption();

    void setScrobbleOption(ScrobbleOption scrobbleOption);

    MultichannelMode getMultichannelMode();

    MultichannelMode getDefaultMultichannelMode();

    void setMultichannelMode(MultichannelMode multichannelMode);
}
