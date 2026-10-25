package indi.etern.musichud.platform.mod.forgeConfig.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.beans.option.ScrobbleOption;
import indi.etern.musichud.beans.option.MultichannelMode;
import indi.etern.musichud.client.ui.render.HorizontalAlign;
import indi.etern.musichud.client.ui.render.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class ClientConfigDefinition implements ClientConfig {
    public static Pair<ClientConfigDefinition, ModConfigSpec> configure;
    @Getter
    private static ClientConfigDefinition instance;

    static {
        configure();
    }

    private final ModConfigSpec.ConfigValue<Boolean> enable;
    private final ModConfigSpec.ConfigValue<Boolean> showTranslatedCnLyrics;
    private final ModConfigSpec.ConfigValue<Boolean> enableLyricBlur;
    private final ModConfigSpec.ConfigValue<Boolean> disableVanillaMusic;
    private final ModConfigSpec.ConfigValue<Boolean> enableHud;
    private final ModConfigSpec.ConfigValue<Boolean> enableLyricsSidebar;
    private final ModConfigSpec.ConfigValue<Boolean> hideHudWhenNotPlaying;
    private final ModConfigSpec.ConfigValue<Boolean> enableMarqueeText;
    private final ModConfigSpec.ConfigValue<Boolean> showHudLayoutBoxes;
    private final ModConfigSpec.ConfigValue<Boolean> showThemeColor;
    private final ModConfigSpec.ConfigValue<Boolean> forceGL32Path;
    private final ModConfigSpec.ConfigValue<Boolean> forceRgba8Fallback;
    private final ModConfigSpec.ConfigValue<Boolean> useProceduralWarp;
    private final ModConfigSpec.ConfigValue<Integer> guiBackgroundBlurRadius;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverOpacity;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverLayerOpacity;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundSaturation;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundAudioScaleAmount;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundAudioLowWeight;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundAudioMidWeight;
    private final ModConfigSpec.ConfigValue<Integer> guiBackgroundCoverRotationSpeed;
    private final ModConfigSpec.ConfigValue<Integer> guiBackgroundFluidBlurRadius;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverDriftX;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverDriftY;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverDriftSpeed;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverOrbitDriftX;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundCoverOrbitDriftY;
    private final ModConfigSpec.ConfigValue<Boolean> mixWithVanillaSoundVolume;
    private final ModConfigSpec.ConfigValue<Integer> soundVolume;
    private final ModConfigSpec.ConfigValue<Integer> soundVolumeInterval;
    private final ModConfigSpec.ConfigValue<Boolean> muted;
    private final ModConfigSpec.ConfigValue<String> primaryChosenQuality;
    private final ModConfigSpec.ConfigValue<String> scrobbleOption;
    private final ModConfigSpec.ConfigValue<String> multichannelMode;
    private final ModConfigSpec.ConfigValue<Boolean> enableGuiFluidBackground;
    private final ModConfigSpec.ConfigValue<Double> fluidBackgroundAlpha;
    private final ModConfigSpec.ConfigValue<Double> fluidBackgroundBrightness;
    private final ModConfigSpec.ConfigValue<Double> solidBackgroundAlpha;
    private final ModConfigSpec.ConfigValue<Double> solidBackgroundBrightness;
    private final ModConfigSpec.ConfigValue<Double> hudBackgroundMixAlpha;

    private final ModConfigSpec.ConfigValue<String> hudVerticalPosition;
    private final ModConfigSpec.ConfigValue<String> hudHorizontalPosition;
    private final ModConfigSpec.ConfigValue<Integer> hudOffsetX;
    private final ModConfigSpec.ConfigValue<Integer> hudOffsetY;
    private final ModConfigSpec.ConfigValue<Integer> hudWidth;
    private final ModConfigSpec.ConfigValue<Integer> hudHeight;
    private final ModConfigSpec.ConfigValue<Integer> hudCornerRadius;
    private final ModConfigSpec.ConfigValue<String> clientCookie;
    private final ModConfigSpec.ConfigValue<String> clientAccountConfig;
    private final ModConfigSpec.ConfigValue<Boolean> enabledInIntegratedServer;
    private final ModConfigSpec.ConfigValue<Boolean> enableAutoConnect;
    private final ModConfigSpec.ConfigValue<Boolean> enableIsolatedMode;
    private final ModConfigSpec.ConfigValue<String> autoConnectServerFilterType;
    private final ModConfigSpec.ConfigValue<String> autoConnectBlackList;
    private final ModConfigSpec.ConfigValue<String> autoConnectWhiteList;
    @Setter
    @Getter
    private boolean configured;

    ClientConfigDefinition(ModConfigSpec.Builder builder) {
        enable = builder
                .comment("Enable Music HUD Functions")
                .translation(MusicHud.MOD_ID + ".config.common.enable")
                .define("enable", true);
        showTranslatedCnLyrics = builder
                .comment("Show translated Chinese lyrics")
                .translation(MusicHud.MOD_ID + ".config.common.showTranslatedCnLyrics")
                .define("showTranslatedCnLyrics", true);
        enableLyricBlur = builder
                .comment("Defocus (blur) non-highlighted lyric lines; the highlighted line stays sharp")
                .translation(MusicHud.MOD_ID + ".config.common.enableLyricBlur")
                .define("enableLyricBlur", true);
        disableVanillaMusic = builder
                .comment("Disable vanilla game music")
                .translation(MusicHud.MOD_ID + ".config.common.disableVanillaMusicWhilePlaying")
                .define("disableVanillaMusic", true);
        enableHud = builder
                .comment("Enable hud")
                .translation(MusicHud.MOD_ID + ".config.common.enableHud")
                .define("enableHud", true);
        enableLyricsSidebar = builder
                .comment("Enable lyrics sidebar in gui")
                .translation(MusicHud.MOD_ID + ".config.common.enableLyricsSidebar")
                .define("enableLyricsSidebar", true);
        enableMarqueeText = builder
                .comment("Enable marquee animation on overflow text")
                .translation(MusicHud.MOD_ID + ".config.common.enableMarqueeText")
                .define("enableMarqueeText", true);
        showHudLayoutBoxes = builder
                .comment("Show HUD layout debug boxes")
                .translation(MusicHud.MOD_ID + ".config.debug.showHudLayoutBoxes")
                .define("showHudLayoutBoxes", false);
        showThemeColor = builder
                .comment("Show extracted theme color debug")
                .translation(MusicHud.MOD_ID + ".config.debug.showThemeColor")
                .define("showThemeColor", false);
        forceGL32Path = builder
                .comment("Force the OpenGL 3.2 compatible background texture path for debugging")
                .translation(MusicHud.MOD_ID + ".config.debug.forceGL32Path")
                .define("forceGL32Path", false);
        forceRgba8Fallback = builder
                .comment("Force RGBA8 background render targets for debugging")
                .translation(MusicHud.MOD_ID + ".config.debug.forceRgba8Fallback")
                .define("forceRgba8Fallback", false);
        useProceduralWarp = builder
                .comment("Use the clean procedural forward warp for the cover background")
                .translation(MusicHud.MOD_ID + ".config.debug.useProceduralWarp")
                .define("useProceduralWarp", true);
        guiBackgroundBlurRadius = builder
                .comment("GUI and HUD exp background blur radius in physical pixels (0 disables blur)")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundBlurRadius")
                .defineInRange("guiBackgroundBlurRadius", 32, 0, 1024);
        guiBackgroundCoverOpacity = builder
                .comment("Background cover overlay opacity (0 disables the cover overlay)")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverOpacity")
                .defineInRange("guiBackgroundCoverOpacity", 0.35, 0, 1);
        guiBackgroundCoverLayerOpacity = builder
                .comment("Opacity used when compositing the three cover layers")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverLayerOpacity")
                .defineInRange("guiBackgroundCoverLayerOpacity", 0.35, 0, 1);
        guiBackgroundSaturation = builder
                .comment("Saturation applied to the exp background (1 = original)")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundSaturation")
                .defineInRange("guiBackgroundSaturation", 1.0, 0, 2);
        guiBackgroundAudioScaleAmount = builder
                .comment("Audio-reactive fluid layer zoom amount (0 disables it)")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundAudioScaleAmount")
                .defineInRange("guiBackgroundAudioScaleAmount", 0.15, 0, 0.5);
        guiBackgroundAudioLowWeight = builder
                .comment("Low-band weight for the audio-reactive fluid zoom")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundAudioLowWeight")
                .defineInRange("guiBackgroundAudioLowWeight", 2.0 / 3.0, 0, 1);
        guiBackgroundAudioMidWeight = builder
                .comment("Mid-band weight for the audio-reactive fluid zoom")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundAudioMidWeight")
                .defineInRange("guiBackgroundAudioMidWeight", 1.0 / 3.0, 0, 1);
        guiBackgroundCoverRotationSpeed = builder
                .comment("Background cover clockwise rotation speed in degrees per second")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverRotationSpeed")
                .defineInRange("guiBackgroundCoverRotationSpeed", 8, 0, 360);
        guiBackgroundFluidBlurRadius = builder
                .comment("Fluid cover background blur radius in physical pixels (0 disables fluid blur)")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundFluidBlurRadius")
                .defineInRange("guiBackgroundFluidBlurRadius", 32, 0, 1024);
        guiBackgroundCoverDriftX = builder
                .comment("Background cover horizontal drift amplitude in normalized canvas units")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverDriftX")
                .defineInRange("guiBackgroundCoverDriftX", 0.25, 0, 0.5);
        guiBackgroundCoverDriftY = builder
                .comment("Background cover vertical drift amplitude in normalized canvas units")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverDriftY")
                .defineInRange("guiBackgroundCoverDriftY", 0.15, 0, 0.5);
        guiBackgroundCoverDriftSpeed = builder
                .comment("Background cover drift speed multiplier")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverDriftSpeed")
                .defineInRange("guiBackgroundCoverDriftSpeed", 1.0, 0, 4);
        guiBackgroundCoverOrbitDriftX = builder
                .comment("Background cover orbit center horizontal drift amplitude")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverOrbitDriftX")
                .defineInRange("guiBackgroundCoverOrbitDriftX", 0.1, 0, 0.5);
        guiBackgroundCoverOrbitDriftY = builder
                .comment("Background cover orbit center vertical drift amplitude")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundCoverOrbitDriftY")
                .defineInRange("guiBackgroundCoverOrbitDriftY", 0.05, 0, 0.5);
        hideHudWhenNotPlaying = builder
                .comment("Hide hud when not playing music")
                .translation(MusicHud.MOD_ID + ".config.common.autoHide")
                .define("hideHudWhenNotPlaying", true);
        primaryChosenQuality = builder
                .comment("Primary chosen quality")
                .translation(MusicHud.MOD_ID + ".config.common.primaryChosenQuality")
                .define("primaryChosenQuality", Quality.LOSSLESS.name());
        scrobbleOption = builder
                .comment("Scrobbling scope")
                .translation(MusicHud.MOD_ID + ".config.common.scrobbleOption")
                .define("scrobbleOption", ScrobbleOption.ONLY_SELF.name());
        multichannelMode = builder
                .comment("How to handle audio with more than 2 channels (discrete multichannel via AL_EXT_MCFORMATS vs software downmix to stereo)")
                .translation(MusicHud.MOD_ID + ".config.common.multichannelMode")
                .define("multichannelMode", MultichannelMode.PREFER_DISCRETE.name());
        enableGuiFluidBackground = builder
                .comment("Enable fluid background on the main GUI")
                .translation(MusicHud.MOD_ID + ".config.common.enableGuiFluidBackground")
                .define("enableGuiFluidBackground", true);
        fluidBackgroundAlpha = builder
                .comment("Fluid GUI background opacity")
                .translation(MusicHud.MOD_ID + ".config.common.fluidBackgroundAlpha")
                .defineInRange("guiBackgroundAlpha", 0.75, 0, 1);
        fluidBackgroundBrightness = builder
                .comment("Fluid GUI background brightness")
                .translation(MusicHud.MOD_ID + ".config.common.fluidBackgroundBrightness")
                .defineInRange("guiBackgroundBrightness", 0.1, 0, 1);
        solidBackgroundAlpha = builder
                .comment("Solid GUI background opacity")
                .translation(MusicHud.MOD_ID + ".config.common.solidBackgroundAlpha")
                .defineInRange("solidBackgroundAlpha", 0.75, 0, 1);
        solidBackgroundBrightness = builder
                .comment("Solid GUI background brightness")
                .translation(MusicHud.MOD_ID + ".config.common.solidBackgroundBrightness")
                .defineInRange("solidBackgroundBrightness", 0.1, 0, 1);
        hudBackgroundMixAlpha = builder
                .comment("Hud Background Mix Alpha")
                .translation(MusicHud.MOD_ID + ".config.common.hudBackgroundMixAlpha")
                .defineInRange("hudBackgroundMixAlpha", 0.65, 0, 1);
        mixWithVanillaSoundVolume = builder
                .comment("Mix Sound Volume with Vanilla Music Sound Volume")
                .translation(MusicHud.MOD_ID + ".config.common.mixWithVanillaSoundVolume")
                .define("mixWithVanillaSoundVolume", true);
        muted = builder
                .comment("Record Muted Switch")
                .translation(MusicHud.MOD_ID + ".config.common.muted")
                .define("Muted", false);
        soundVolume = builder
                .comment("Sound Volume for audio from Music HUD")
                .translation(MusicHud.MOD_ID + ".config.common.soundVolume")
                .defineInRange("soundVolume", 100, 0, 100);
        soundVolumeInterval = builder
                .comment("Sound Volume Interval for Hot Key Adjust")
                .translation(MusicHud.MOD_ID + ".config.common.soundVolumeInterval")
                .defineInRange("soundVolumeInterval", 10, 1, 100);
        hudVerticalPosition = builder
                .comment("Vertical position (TOP|CENTER|BOTTOM)")
                .translation(MusicHud.MOD_ID + ".config.layout.verticalAlign")
                .define("verticalPosition", VerticalAlign.BOTTOM.name());
        hudHorizontalPosition = builder
                .comment("Horizontal position (LEFT|CENTER|RIGHT)")
                .translation(MusicHud.MOD_ID + ".config.layout.horizontalAlign")
                .define("horizontalPosition", HorizontalAlign.CENTER.name());
        hudOffsetX = builder
                .comment("Hud offset x")
                .translation(MusicHud.MOD_ID + ".config.layout.offsetX")
                .define("hudOffsetX", 0);
        hudOffsetY = builder
                .comment("Hud offset y")
                .translation(MusicHud.MOD_ID + ".config.layout.offsetY")
                .define("hudOffsetY", 68);
        hudWidth = builder
                .comment("Hud width")
                .translation(MusicHud.MOD_ID + ".config.layout.hudWidth")
                .define("hudWidth", 180);
        hudHeight = builder
                .comment("Hud height")
                .translation(MusicHud.MOD_ID + ".config.layout.hudHeight")
                .define("hudHeight", 52);
        hudCornerRadius = builder
                .comment("Hud rounded corner radius")
                .translation(MusicHud.MOD_ID + ".config.layout.hudCornerRadius")
                .define("hudCornerRadius", 8);
        clientCookie = builder
                .comment("Client NCM cookie json")
                .translation(MusicHud.MOD_ID + ".internal.clientCookie")
                .define("clientCookie", "");
        clientAccountConfig = builder
                .comment("Client account config json")
                .translation(MusicHud.MOD_ID + ".internal.clientAccountConfig")
                .define("clientAccountConfig", "");
        enabledInIntegratedServer = builder
                .comment("Enable embedded server (To enable Music HUD in singleplayer or LAN multiplayer)")
                .translation(MusicHud.MOD_ID + ".config.integratedServer.enable")
                .define("enabledInIntegratedServer", true);
        enableAutoConnect = builder
                .comment("Enable auto connect")
                .translation(MusicHud.MOD_ID + ".config.autoConnect")
                .define("enableAutoConnect", true);
        enableIsolatedMode = builder
                .comment("Enable client only mode")
                .translation(MusicHud.MOD_ID + ".config.clientOnlyMode")
                .define("enableClientOnlyMode", true);
        autoConnectServerFilterType = builder
                .comment("Auto connecting servers filter type (white list / black list)")
                .translation(MusicHud.MOD_ID + ".config.autoConnectServerFilterType")
                .define("autoConnectServerFilterType", AutoConnectServerFilterType.BLACK_LIST.name());
        autoConnectBlackList = builder
                .comment("Auto connecting servers black list")
                .translation(MusicHud.MOD_ID + ".config.autoConnectBlackList")
                .define("autoConnectBlackList", "[]");
        autoConnectWhiteList = builder
                .comment("Auto connecting servers white list")
                .translation(MusicHud.MOD_ID + ".config.autoConnectWhiteList")
                .define("autoConnectWhiteList", "[]");
        instance = this;
    }

    public static void configure() {
        if (configure == null) {
            configure = new ModConfigSpec.Builder().configure(ClientConfigDefinition::new);
        }
    }

    @Override
    public boolean getEnableAutoConnect() {
        return this.enableAutoConnect.get();
    }

    @Override
    public boolean getDefaultEnableAutoConnect() {
        return enableAutoConnect.getDefault();
    }

    @Override
    public void setEnableAutoConnect(boolean autoConnect) {
        this.enableAutoConnect.set(autoConnect);
    }

    @Override
    public boolean getEnableIsolatedMode() {
        return this.enableIsolatedMode.get();
    }

    @Override
    public boolean getDefaultEnableIsolatedMode() {
        return enableIsolatedMode.getDefault();
    }

    @Override
    public void setEnableIsolatedMode(boolean autoConnect) {
        this.enableIsolatedMode.set(autoConnect);
    }

    @Override
    public AutoConnectServerFilterType getConnectServerFilterType() {
        return AutoConnectServerFilterType.valueOf(autoConnectServerFilterType.get());
    }

    @Override
    public AutoConnectServerFilterType getDefaultConnectServerFilterType() {
        return AutoConnectServerFilterType.valueOf(autoConnectServerFilterType.getDefault());
    }

    @Override
    public void setConnectServerFilterType(AutoConnectServerFilterType type) {
        autoConnectServerFilterType.set(type.name());
    }

    @Override
    public List<String> getBlackList() {
        //noinspection unchecked
        return JsonUtil.gson.fromJson(autoConnectBlackList.get(), List.class);
    }

    @Override
    public List<String> getDefaultBlackList() {
        return List.of();
    }

    @Override
    public void setBlackList(List<String> blackList) {
        autoConnectBlackList.set(JsonUtil.gson.toJson(blackList));
    }

    @Override
    public List<String> getWhiteList() {
        //noinspection unchecked
        return JsonUtil.gson.fromJson(autoConnectWhiteList.get(), List.class);
    }

    @Override
    public List<String> getDefaultWhiteList() {
        return List.of();
    }

    @Override
    public void setWhiteList(List<String> whiteList) {
        autoConnectWhiteList.set(JsonUtil.gson.toJson(whiteList));
    }

    @Override
    public boolean getEnable() {
        return enable.get();
    }

    @Override
    public void setEnable(boolean enable) {
        this.enable.set(enable);
    }

    @Override
    public boolean getDefaultEnable() {
        return enable.getDefault();
    }

    @Override
    public boolean getShowTranslatedCnLyrics() {
        return showTranslatedCnLyrics.get();
    }

    @Override
    public boolean getDefaultShowTranslatedCnLyrics() {
        return showTranslatedCnLyrics.getDefault();
    }

    @Override
    public void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics) {
        this.showTranslatedCnLyrics.set(showTranslatedCnLyrics);
    }

    @Override
    public boolean getEnableLyricBlur() {
        return enableLyricBlur.get();
    }

    @Override
    public boolean getDefaultEnableLyricBlur() {
        return enableLyricBlur.getDefault();
    }

    @Override
    public void setEnableLyricBlur(boolean enableLyricBlur) {
        this.enableLyricBlur.set(enableLyricBlur);
    }

    @Override
    public boolean getDisableVanillaMusic() {
        return disableVanillaMusic.get();
    }

    @Override
    public void setDisableVanillaMusic(boolean disableVanillaMusic) {
        this.disableVanillaMusic.set(disableVanillaMusic);
    }

    @Override
    public boolean getDefaultDisableVanillaMusic() {
        return disableVanillaMusic.getDefault();
    }

    @Override
    public boolean getHideHudWhenNotPlaying() {
        return hideHudWhenNotPlaying.get();
    }

    @Override
    public void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying) {
        this.hideHudWhenNotPlaying.set(hideHudWhenNotPlaying);
    }

    @Override
    public boolean getDefaultHideHudWhenNotPlaying() {
        return hideHudWhenNotPlaying.getDefault();
    }

    @Override
    public boolean getEnableHud() {
        return enableHud.get();
    }

    @Override
    public boolean getDefaultEnableHud() {
        return enableHud.getDefault();
    }

    @Override
    public void setEnableHud(boolean enableHud) {
        this.enableHud.set(enableHud);
    }

    @Override
    public boolean getEnableLyricsSidebar() {
        return enableLyricsSidebar.get();
    }

    @Override
    public boolean getDefaultEnableLyricsSidebar() {
        return enableLyricsSidebar.getDefault();
    }

    @Override
    public void setEnableLyricsSidebar(boolean enableLyricsSidebar) {
        this.enableLyricsSidebar.set(enableLyricsSidebar);
    }

    @Override
    public Quality getPrimaryChosenQuality() {
        return Quality.valueOf(primaryChosenQuality.get());
    }

    @Override
    public Quality getDefaultPrimaryChosenQuality() {
        return Quality.valueOf(primaryChosenQuality.getDefault());
    }

    @Override
    public void setPrimaryChosenQuality(Quality primaryChosenQuality) {
        this.primaryChosenQuality.set(primaryChosenQuality.name());
    }

    @Override
    public String getHudVerticalPosition() {
        return hudVerticalPosition.get();
    }

    @Override
    public String getDefaultHudVerticalPosition() {
        return hudVerticalPosition.getDefault();
    }

    @Override
    public void setHudVerticalPosition(String hudVerticalPosition) {
        this.hudVerticalPosition.set(hudVerticalPosition);
    }

    @Override
    public String getHudHorizontalPosition() {
        return hudHorizontalPosition.get();
    }

    @Override
    public String getDefaultHudHorizontalPosition() {
        return hudHorizontalPosition.getDefault();
    }

    @Override
    public void setHudHorizontalPosition(String hudHorizontalPosition) {
        this.hudHorizontalPosition.set(hudHorizontalPosition);
    }

    @Override
    public int getHudOffsetX() {
        return hudOffsetX.get();
    }

    @Override
    public int getDefaultHudOffsetX() {
        return hudOffsetX.getDefault();
    }

    @Override
    public void setHudOffsetX(int hudOffsetX) {
        this.hudOffsetX.set(hudOffsetX);
    }

    @Override
    public int getHudOffsetY() {
        return hudOffsetY.get();
    }

    @Override
    public int getDefaultHudOffsetY() {
        return hudOffsetY.getDefault();
    }

    @Override
    public void setHudOffsetY(int hudOffsetY) {
        this.hudOffsetY.set(hudOffsetY);
    }

    @Override
    public int getHudWidth() {
        return hudWidth.get();
    }

    @Override
    public int getDefaultHudWidth() {
        return hudWidth.getDefault();
    }

    @Override
    public void setHudWidth(int hudWidth) {
        this.hudWidth.set(hudWidth);
    }

    @Override
    public int getHudHeight() {
        return hudHeight.get();
    }

    @Override
    public int getDefaultHudHeight() {
        return hudHeight.getDefault();
    }

    @Override
    public void setHudHeight(int hudHeight) {
        this.hudHeight.set(hudHeight);
    }

    @Override
    public int getHudCornerRadius() {
        return hudCornerRadius.get();
    }

    @Override
    public int getDefaultHudCornerRadius() {
        return hudCornerRadius.getDefault();
    }

    @Override
    public void setHudCornerRadius(int hudCornerRadius) {
        this.hudCornerRadius.set(hudCornerRadius);
    }

    @Override
    public LoginCookieInfo getClientCookie() {
        return JsonUtil.gson.fromJson(clientCookie.get(), LoginCookieInfo.class);
    }

    @Override
    public void setClientCookie(LoginCookieInfo clientCookie) {
        this.clientCookie.set(JsonUtil.gson.toJson(clientCookie));
    }

    @Override
    public ProfileConfigData getClientAccountConfig() {
        return JsonUtil.gson.fromJson(clientAccountConfig.get(), ProfileConfigData.class);
    }

    @Override
    public void setClientAccountConfig(ProfileConfigData clientAccountConfig) {
        this.clientAccountConfig.set(JsonUtil.gson.toJson(clientAccountConfig));
    }

    @Override
    public boolean getEnabledInIntegratedServer() {
        return enabledInIntegratedServer.get();
    }

    @Override
    public boolean getDefaultEnabledInIntegratedServer() {
        return enabledInIntegratedServer.getDefault();
    }

    @Override
    public void setEnabledInIntegratedServer(boolean enabledInIntegratedServer) {
        this.enabledInIntegratedServer.set(enabledInIntegratedServer);
    }

    @Override
    public synchronized void save() {
        configure.getRight().save();
    }

    @Override
    public ScrobbleOption getScrobbleOption() {
        return ScrobbleOption.valueOf(scrobbleOption.get());
    }

    @Override
    public ScrobbleOption getDefaultScrobbleOption() {
        return ScrobbleOption.valueOf(scrobbleOption.getDefault());
    }

    @Override
    public void setScrobbleOption(ScrobbleOption scrobbleOption) {
        this.scrobbleOption.set(scrobbleOption.name());
    }

    @Override
    public MultichannelMode getMultichannelMode() {
        return MultichannelMode.valueOf(multichannelMode.get());
    }

    @Override
    public MultichannelMode getDefaultMultichannelMode() {
        return MultichannelMode.valueOf(multichannelMode.getDefault());
    }

    @Override
    public void setMultichannelMode(MultichannelMode multichannelMode) {
        this.multichannelMode.set(multichannelMode.name());
    }

    @Override
    public boolean getEnableGuiFluidBackground() {
        return enableGuiFluidBackground.get();
    }

    @Override
    public boolean getDefaultEnableGuiFluidBackground() {
        return enableGuiFluidBackground.getDefault();
    }

    @Override
    public void setEnableGuiFluidBackground(boolean enableGuiFluidBackground) {
        this.enableGuiFluidBackground.set(enableGuiFluidBackground);
    }

    @Override
    public double getFluidBackgroundAlpha() {
        return fluidBackgroundAlpha.get();
    }

    @Override
    public double getDefaultFluidBackgroundAlpha() {
        return fluidBackgroundAlpha.getDefault();
    }

    @Override
    public void setFluidBackgroundAlpha(double fluidBackgroundAlpha) {
        this.fluidBackgroundAlpha.set(fluidBackgroundAlpha);
    }

    @Override
    public double getFluidBackgroundBrightness() {
        return fluidBackgroundBrightness.get();
    }

    @Override
    public double getDefaultFluidBackgroundBrightness() {
        return fluidBackgroundBrightness.getDefault();
    }

    @Override
    public void setFluidBackgroundBrightness(double fluidBackgroundBrightness) {
        this.fluidBackgroundBrightness.set(fluidBackgroundBrightness);
    }

    @Override
    public double getSolidBackgroundAlpha() {
        return solidBackgroundAlpha.get();
    }

    @Override
    public double getDefaultSolidBackgroundAlpha() {
        return solidBackgroundAlpha.getDefault();
    }

    @Override
    public void setSolidBackgroundAlpha(double solidBackgroundAlpha) {
        this.solidBackgroundAlpha.set(solidBackgroundAlpha);
    }

    @Override
    public double getSolidBackgroundBrightness() {
        return solidBackgroundBrightness.get();
    }

    @Override
    public double getDefaultSolidBackgroundBrightness() {
        return solidBackgroundBrightness.getDefault();
    }

    @Override
    public void setSolidBackgroundBrightness(double solidBackgroundBrightness) {
        this.solidBackgroundBrightness.set(solidBackgroundBrightness);
    }

    @Override
    public double getHudBackgroundMixAlpha() {
        return hudBackgroundMixAlpha.get();
    }

    @Override
    public double getDefaultHudBackgroundMixAlpha() {
        return hudBackgroundMixAlpha.getDefault();
    }

    @Override
    public void setHudBackgroundMixAlpha(double hudBackgroundMixAlpha) {
        this.hudBackgroundMixAlpha.set(hudBackgroundMixAlpha);
    }

    @Override
    public boolean getMixWithVanillaSoundVolume() {
        return mixWithVanillaSoundVolume.get();
    }

    @Override
    public boolean getDefaultMixWithVanillaSoundVolume() {
        return mixWithVanillaSoundVolume.getDefault();
    }

    @Override
    public void setMixWithVanillaSoundVolume(boolean mixWithVanillaSoundVolume) {
        this.mixWithVanillaSoundVolume.set(mixWithVanillaSoundVolume);
    }

    @Override
    public boolean getMuted() {
        return muted.get();
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }

    @Override
    public int getSoundVolume() {
        return soundVolume.get();
    }

    @Override
    public int getDefaultSoundVolume() {
        return soundVolume.getDefault();
    }

    @Override
    public void setSoundVolume(int soundVolume) {
        if (soundVolume == 0) {
            this.muted.set(true);
        } else {
            this.muted.set(false);
            this.soundVolume.set(soundVolume);
        }
    }

    @Override
    public void forceSetSoundVolume(int soundVolume) {
        this.muted.set(soundVolume == 0);
        this.soundVolume.set(soundVolume);
    }

    @Override
    public int getSoundVolumeInterval() {
        return soundVolumeInterval.get();
    }

    @Override
    public int getDefaultSoundVolumeInterval() {
        return soundVolumeInterval.getDefault();
    }

    @Override
    public void setSoundVolumeInterval(int soundVolume) {
        this.soundVolumeInterval.set(soundVolume);
    }

    @Override
    public boolean getEnableMarqueeText() {
        return enableMarqueeText.get();
    }

    @Override
    public boolean getDefaultEnableMarqueeText() {
        return enableMarqueeText.getDefault();
    }

    @Override
    public void setEnableMarqueeText(boolean enable) {
        enableMarqueeText.set(enable);
    }

    @Override
    public boolean getShowHudLayoutBoxes() {
        return showHudLayoutBoxes.get();
    }

    @Override
    public boolean getDefaultShowHudLayoutBoxes() {
        return showHudLayoutBoxes.getDefault();
    }

    @Override
    public void setShowHudLayoutBoxes(boolean showHudLayoutBoxes) {
        this.showHudLayoutBoxes.set(showHudLayoutBoxes);
    }

    @Override
    public boolean getShowThemeColor() {
        return showThemeColor.get();
    }

    @Override
    public boolean getDefaultShowThemeColor() {
        return showThemeColor.getDefault();
    }

    @Override
    public void setShowThemeColor(boolean showThemeColor) {
        this.showThemeColor.set(showThemeColor);
    }

    @Override
    public boolean getForceGL32Path() {
        return forceGL32Path.get();
    }

    @Override
    public boolean getDefaultForceGL32Path() {
        return forceGL32Path.getDefault();
    }

    @Override
    public void setForceGL32Path(boolean force) {
        forceGL32Path.set(force);
    }

    @Override
    public boolean getForceRgba8Fallback() {
        return forceRgba8Fallback.get();
    }

    @Override
    public boolean getDefaultForceRgba8Fallback() {
        return forceRgba8Fallback.getDefault();
    }

    @Override
    public void setForceRgba8Fallback(boolean force) {
        forceRgba8Fallback.set(force);
    }

    @Override
    public boolean getUseProceduralWarp() {
        return useProceduralWarp.get();
    }

    @Override
    public boolean getDefaultUseProceduralWarp() {
        return useProceduralWarp.getDefault();
    }

    @Override
    public void setUseProceduralWarp(boolean use) {
        useProceduralWarp.set(use);
    }

    @Override
    public int getGuiBackgroundBlurRadius() {
        return guiBackgroundBlurRadius.get();
    }

    @Override
    public int getDefaultGuiBackgroundBlurRadius() {
        return guiBackgroundBlurRadius.getDefault();
    }

    @Override
    public void setGuiBackgroundBlurRadius(int radius) {
        guiBackgroundBlurRadius.set(Math.clamp(radius, 0, 1024));
    }

    @Override
    public double getGuiBackgroundCoverOpacity() {
        return guiBackgroundCoverOpacity.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverOpacity() {
        return guiBackgroundCoverOpacity.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverOpacity(double opacity) {
        guiBackgroundCoverOpacity.set(Math.clamp(opacity, 0, 1));
    }

    @Override
    public double getGuiBackgroundCoverLayerOpacity() {
        return guiBackgroundCoverLayerOpacity.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverLayerOpacity() {
        return guiBackgroundCoverLayerOpacity.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverLayerOpacity(double opacity) {
        guiBackgroundCoverLayerOpacity.set(Math.clamp(opacity, 0, 1));
    }

    @Override
    public double getGuiBackgroundSaturation() {
        return guiBackgroundSaturation.get();
    }

    @Override
    public double getDefaultGuiBackgroundSaturation() {
        return guiBackgroundSaturation.getDefault();
    }

    @Override
    public void setGuiBackgroundSaturation(double saturation) {
        guiBackgroundSaturation.set(Math.clamp(saturation, 0, 2));
    }

    @Override
    public double getGuiBackgroundAudioScaleAmount() {
        return guiBackgroundAudioScaleAmount.get();
    }

    @Override
    public double getDefaultGuiBackgroundAudioScaleAmount() {
        return guiBackgroundAudioScaleAmount.getDefault();
    }

    @Override
    public void setGuiBackgroundAudioScaleAmount(double amount) {
        guiBackgroundAudioScaleAmount.set(Math.clamp(amount, 0, 0.5));
    }

    @Override
    public double getGuiBackgroundAudioLowWeight() {
        return guiBackgroundAudioLowWeight.get();
    }

    @Override
    public double getDefaultGuiBackgroundAudioLowWeight() {
        return guiBackgroundAudioLowWeight.getDefault();
    }

    @Override
    public void setGuiBackgroundAudioLowWeight(double weight) {
        guiBackgroundAudioLowWeight.set(Math.clamp(weight, 0, 1));
    }

    @Override
    public double getGuiBackgroundAudioMidWeight() {
        return guiBackgroundAudioMidWeight.get();
    }

    @Override
    public double getDefaultGuiBackgroundAudioMidWeight() {
        return guiBackgroundAudioMidWeight.getDefault();
    }

    @Override
    public void setGuiBackgroundAudioMidWeight(double weight) {
        guiBackgroundAudioMidWeight.set(Math.clamp(weight, 0, 1));
    }

    @Override
    public int getGuiBackgroundFluidBlurRadius() {
        return guiBackgroundFluidBlurRadius.get();
    }

    @Override
    public int getDefaultGuiBackgroundFluidBlurRadius() {
        return guiBackgroundFluidBlurRadius.getDefault();
    }

    @Override
    public void setGuiBackgroundFluidBlurRadius(int radius) {
        guiBackgroundFluidBlurRadius.set(Math.clamp(radius, 0, 1024));
    }

    @Override
    public int getGuiBackgroundCoverRotationSpeed() {
        return guiBackgroundCoverRotationSpeed.get();
    }

    @Override
    public int getDefaultGuiBackgroundCoverRotationSpeed() {
        return guiBackgroundCoverRotationSpeed.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverRotationSpeed(int degreesPerSecond) {
        guiBackgroundCoverRotationSpeed.set(Math.clamp(degreesPerSecond, 0, 360));
    }

    @Override
    public double getGuiBackgroundCoverDriftX() {
        return guiBackgroundCoverDriftX.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverDriftX() {
        return guiBackgroundCoverDriftX.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverDriftX(double amplitude) {
        guiBackgroundCoverDriftX.set(Math.clamp(amplitude, 0, 0.5));
    }

    @Override
    public double getGuiBackgroundCoverDriftY() {
        return guiBackgroundCoverDriftY.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverDriftY() {
        return guiBackgroundCoverDriftY.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverDriftY(double amplitude) {
        guiBackgroundCoverDriftY.set(Math.clamp(amplitude, 0, 0.5));
    }

    @Override
    public double getGuiBackgroundCoverDriftSpeed() {
        return guiBackgroundCoverDriftSpeed.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverDriftSpeed() {
        return guiBackgroundCoverDriftSpeed.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverDriftSpeed(double speed) {
        guiBackgroundCoverDriftSpeed.set(Math.clamp(speed, 0, 4));
    }

    @Override
    public double getGuiBackgroundCoverOrbitDriftX() {
        return guiBackgroundCoverOrbitDriftX.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverOrbitDriftX() {
        return guiBackgroundCoverOrbitDriftX.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverOrbitDriftX(double amplitude) {
        guiBackgroundCoverOrbitDriftX.set(Math.clamp(amplitude, 0, 0.5));
    }

    @Override
    public double getGuiBackgroundCoverOrbitDriftY() {
        return guiBackgroundCoverOrbitDriftY.get();
    }

    @Override
    public double getDefaultGuiBackgroundCoverOrbitDriftY() {
        return guiBackgroundCoverOrbitDriftY.getDefault();
    }

    @Override
    public void setGuiBackgroundCoverOrbitDriftY(double amplitude) {
        guiBackgroundCoverOrbitDriftY.set(Math.clamp(amplitude, 0, 0.5));
    }
}