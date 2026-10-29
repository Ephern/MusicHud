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
    private final ModConfigSpec.ConfigValue<Boolean> forceGL32Path;
    private final ModConfigSpec.ConfigValue<Boolean> forceRgba8Fallback;
    private final ModConfigSpec.ConfigValue<Integer> guiBackgroundBlurRadius;
    private final ModConfigSpec.ConfigValue<Integer> hudBackgroundBlurRadius;
    private final ModConfigSpec.ConfigValue<Integer> guiBackgroundFluidBlurRadius;
    private final ModConfigSpec.ConfigValue<Integer> hudBackgroundFluidBlurRadius;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundFluidOpacity;
    private final ModConfigSpec.ConfigValue<Double> hudBackgroundFluidOpacity;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundSamplingBlackOverlay;
    private final ModConfigSpec.ConfigValue<Double> hudBackgroundSamplingBlackOverlay;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundOverallBrightness;
    private final ModConfigSpec.ConfigValue<Double> guiBackgroundSaturation;
    private final ModConfigSpec.ConfigValue<Double> panelOpacity;
    private final ModConfigSpec.ConfigValue<Double> panelBrightness;
    private final ModConfigSpec.ConfigValue<Boolean> enableSidebarBackground;
    private final ModConfigSpec.ConfigValue<Boolean> mixWithVanillaSoundVolume;
    private final ModConfigSpec.ConfigValue<Integer> soundVolume;
    private final ModConfigSpec.ConfigValue<Integer> soundVolumeInterval;
    private final ModConfigSpec.ConfigValue<Boolean> muted;
    private final ModConfigSpec.ConfigValue<String> primaryChosenQuality;
    private final ModConfigSpec.ConfigValue<String> scrobbleOption;
    private final ModConfigSpec.ConfigValue<String> multichannelMode;

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
        forceGL32Path = builder
                .comment("Force the OpenGL 3.2 compatible background texture path for debugging")
                .translation(MusicHud.MOD_ID + ".config.debug.forceGL32Path")
                .define("forceGL32Path", false);
        forceRgba8Fallback = builder
                .comment("Force RGBA8 background render targets for debugging")
                .translation(MusicHud.MOD_ID + ".config.debug.forceRgba8Fallback")
                .define("forceRgba8Fallback", false);
        guiBackgroundBlurRadius = builder
                .comment("GUI background sampling blur radius in physical pixels (0 disables blur)")
                .translation(MusicHud.MOD_ID + ".config.common.guiBackgroundBlurRadius")
                .defineInRange("guiBackgroundSamplingBlurRadius", 256, 0, 1024);
        hudBackgroundBlurRadius = builder
                .comment("HUD background sampling blur radius in physical pixels (0 disables blur)")
                .translation(MusicHud.MOD_ID + ".config.common.hudBackgroundBlurRadius")
                .defineInRange("hudBackgroundSamplingBlurRadius", 64, 0, 1024);
        guiBackgroundFluidBlurRadius = builder
                .comment("GUI fluid layer blur radius in physical pixels (0 disables fluid blur)")
                .translation(MusicHud.MOD_ID + ".config.common.guiBackgroundFluidBlurRadius")
                .defineInRange("guiFluidBackgroundBlurRadius", 480, 0, 1024);
        hudBackgroundFluidBlurRadius = builder
                .comment("HUD fluid layer blur radius lower bound in physical pixels")
                .translation(MusicHud.MOD_ID + ".config.common.hudBackgroundFluidBlurRadius")
                .defineInRange("hudFluidBackgroundBlurRadius", 96, 0, 1024);
        guiBackgroundFluidOpacity = builder
                .comment("GUI fluid layer final composite opacity (0 disables the fluid layer)")
                .translation(MusicHud.MOD_ID + ".config.common.guiBackgroundFluidOpacity")
                .defineInRange("guiBackgroundFluidOpacity", 1.0, 0, 1);
        hudBackgroundFluidOpacity = builder
                .comment("HUD fluid layer final composite opacity (0 disables the fluid layer)")
                .translation(MusicHud.MOD_ID + ".config.common.hudBackgroundFluidOpacity")
                .defineInRange("hudBackgroundFluidOpacity", 1.0, 0, 1);
        guiBackgroundSamplingBlackOverlay = builder
                .comment("Black overlay applied only to the GUI background sampling path")
                .translation(MusicHud.MOD_ID + ".config.common.guiBackgroundSamplingBlackOverlay")
                .defineInRange("guiBackgroundSamplingBlackOverlay", 0.7, 0, 1);
        hudBackgroundSamplingBlackOverlay = builder
                .comment("Black overlay applied only to the HUD background sampling path")
                .translation(MusicHud.MOD_ID + ".config.common.hudBackgroundSamplingBlackOverlay")
                .defineInRange("hudBackgroundSamplingBlackOverlay", 0.7, 0, 1);
        guiBackgroundOverallBrightness = builder
                .comment("Temporary background brightness debug value")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundOverallBrightness")
                .defineInRange("guiBackgroundOverallBrightness", 0.6, 0, 2);
        guiBackgroundSaturation = builder
                .comment("Temporary natural-saturation/vibrance debug value")
                .translation(MusicHud.MOD_ID + ".config.debug.guiBackgroundSaturation")
                .defineInRange("guiBackgroundSaturation", 1.3, 0, 2);
        panelOpacity = builder
                .comment("Panel background opacity; only affects the sidebar and ConfigView category panels")
                .translation(MusicHud.MOD_ID + ".config.common.panelOpacity")
                .defineInRange("panelOpacity", 0.2, 0, 1);
        panelBrightness = builder
                .comment("Panel background brightness (0 = black, 1 = neutral gray)")
                .translation(MusicHud.MOD_ID + ".config.common.panelBrightness")
                .defineInRange("panelBrightness", 0.25, 0, 1);
        enableSidebarBackground = builder
                .comment("Show background on the main GUI sidebar")
                .translation(MusicHud.MOD_ID + ".config.common.enableSidebarBackground")
                .define("enableSidebarBackground", true);
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
    public int getHudBackgroundBlurRadius() {
        return hudBackgroundBlurRadius.get();
    }

    @Override
    public int getDefaultHudBackgroundBlurRadius() {
        return hudBackgroundBlurRadius.getDefault();
    }

    @Override
    public void setHudBackgroundBlurRadius(int radius) {
        hudBackgroundBlurRadius.set(Math.clamp(radius, 0, 1024));
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
    public int getHudBackgroundFluidBlurRadius() {
        return hudBackgroundFluidBlurRadius.get();
    }

    @Override
    public int getDefaultHudBackgroundFluidBlurRadius() {
        return hudBackgroundFluidBlurRadius.getDefault();
    }

    @Override
    public void setHudBackgroundFluidBlurRadius(int radius) {
        hudBackgroundFluidBlurRadius.set(Math.clamp(radius, 0, 1024));
    }

    @Override
    public double getGuiBackgroundFluidOpacity() {
        return guiBackgroundFluidOpacity.get();
    }

    @Override
    public double getDefaultGuiBackgroundFluidOpacity() {
        return guiBackgroundFluidOpacity.getDefault();
    }

    @Override
    public void setGuiBackgroundFluidOpacity(double opacity) {
        guiBackgroundFluidOpacity.set(Math.clamp(opacity, 0, 1));
    }

    @Override
    public double getHudBackgroundFluidOpacity() {
        return hudBackgroundFluidOpacity.get();
    }

    @Override
    public double getDefaultHudBackgroundFluidOpacity() {
        return hudBackgroundFluidOpacity.getDefault();
    }

    @Override
    public void setHudBackgroundFluidOpacity(double opacity) {
        hudBackgroundFluidOpacity.set(Math.clamp(opacity, 0, 1));
    }

    @Override
    public double getGuiBackgroundSamplingBlackOverlay() {
        return guiBackgroundSamplingBlackOverlay.get();
    }

    @Override
    public double getDefaultGuiBackgroundSamplingBlackOverlay() {
        return guiBackgroundSamplingBlackOverlay.getDefault();
    }

    @Override
    public void setGuiBackgroundSamplingBlackOverlay(double overlay) {
        guiBackgroundSamplingBlackOverlay.set(Math.clamp(overlay, 0, 1));
    }

    @Override
    public double getHudBackgroundSamplingBlackOverlay() {
        return hudBackgroundSamplingBlackOverlay.get();
    }

    @Override
    public double getDefaultHudBackgroundSamplingBlackOverlay() {
        return hudBackgroundSamplingBlackOverlay.getDefault();
    }

    @Override
    public void setHudBackgroundSamplingBlackOverlay(double overlay) {
        hudBackgroundSamplingBlackOverlay.set(Math.clamp(overlay, 0, 1));
    }

    @Override
    public double getGuiBackgroundOverallBrightness() {
        return guiBackgroundOverallBrightness.get();
    }

    @Override
    public double getDefaultGuiBackgroundOverallBrightness() {
        return guiBackgroundOverallBrightness.getDefault();
    }

    @Override
    public void setGuiBackgroundOverallBrightness(double brightness) {
        guiBackgroundOverallBrightness.set(Math.clamp(brightness, 0, 2));
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
    public double getPanelOpacity() {
        return panelOpacity.get();
    }

    @Override
    public double getDefaultPanelOpacity() {
        return panelOpacity.getDefault();
    }

    @Override
    public void setPanelOpacity(double opacity) {
        panelOpacity.set(Math.clamp(opacity, 0, 1));
    }

    @Override
    public double getPanelBrightness() {
        return panelBrightness.get();
    }

    @Override
    public double getDefaultPanelBrightness() {
        return panelBrightness.getDefault();
    }

    @Override
    public void setPanelBrightness(double brightness) {
        panelBrightness.set(Math.clamp(brightness, 0, 1));
    }

    @Override
    public boolean getEnableSidebarBackground() {
        return enableSidebarBackground.get();
    }

    @Override
    public boolean getDefaultEnableSidebarBackground() {
        return enableSidebarBackground.getDefault();
    }

    @Override
    public void setEnableSidebarBackground(boolean enable) {
        enableSidebarBackground.set(enable);
    }
}