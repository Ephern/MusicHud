package indi.etern.musichud.platform.mod.forgeConfig.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientConfigDefinition implements ClientConfig {
    public static Pair<ClientConfigDefinition, ModConfigSpec> configure;
    @Getter
    private static ClientConfigDefinition instance;

    static {
        configure();
    }

    private final ModConfigSpec.ConfigValue<Boolean> enable;
    private final ModConfigSpec.ConfigValue<Boolean> showTranslatedCnLyrics;
    private final ModConfigSpec.ConfigValue<Boolean> disableVanillaMusic;
    private final ModConfigSpec.ConfigValue<Boolean> hideHudWhenNotPlaying;
    private final ModConfigSpec.ConfigValue<Boolean> enableHud;
    private final ModConfigSpec.ConfigValue<Boolean> enableMarqueeText;
    private final ModConfigSpec.ConfigValue<String> primaryChosenQuality;
    private final ModConfigSpec.ConfigValue<Double> mainScreenAdditionalBackgroundDarken;
    private final ModConfigSpec.ConfigValue<Boolean> mixWithVanillaSoundVolume;
    private final ModConfigSpec.ConfigValue<Boolean> muted;
    private final ModConfigSpec.ConfigValue<Integer> soundVolume;
    private final ModConfigSpec.ConfigValue<Integer> soundVolumeInterval;

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
        disableVanillaMusic = builder
                .comment("Disable vanilla game music")
                .translation(MusicHud.MOD_ID + ".config.common.disableVanillaMusicWhilePlaying")
                .define("disableVanillaMusic", true);
        enableHud = builder
                .comment("Enable hud")
                .translation(MusicHud.MOD_ID + ".config.common.enableHud")
                .define("enableHud", true);
        enableMarqueeText = builder
                .comment("Enable marquee animation on overflow text")
                .translation(MusicHud.MOD_ID + ".config.common.enableMarqueeText")
                .define("enableMarqueeText", true);
        hideHudWhenNotPlaying = builder
                .comment("Hide hud when not playing music")
                .translation(MusicHud.MOD_ID + ".config.common.autoHide")
                .define("hideHudWhenNotPlaying", true);
        primaryChosenQuality = builder
                .comment("Primary chosen quality")
                .translation(MusicHud.MOD_ID + ".config.common.primaryChosenQuality")
                .define("primaryChosenQuality", Quality.LOSSLESS.name());
        mainScreenAdditionalBackgroundDarken = builder
                .comment("Main Screen Additional Background Darken Rate")
                .translation(MusicHud.MOD_ID + ".config.common.mainScreenAdditionalBackgroundDarken")
                .defineInRange("mainScreenAdditionalBackgroundDarken", 0.5, 0, 1);
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
                .define("verticalPosition", VerticalAlign.TOP.name());
        hudHorizontalPosition = builder
                .comment("Horizontal position (LEFT|CENTER|RIGHT)")
                .translation(MusicHud.MOD_ID + ".config.layout.horizontalAlign")
                .define("horizontalPosition", HorizontalAlign.LEFT.name());
        hudOffsetX = builder
                .comment("Hud offset x")
                .translation(MusicHud.MOD_ID + ".config.layout.offsetX")
                .define("hudOffsetX", 16);
        hudOffsetY = builder
                .comment("Hud offset y")
                .translation(MusicHud.MOD_ID + ".config.layout.offsetY")
                .define("hudOffsetY", 16);
        hudWidth = builder
                .comment("Hud width")
                .translation(MusicHud.MOD_ID + ".config.layout.hudWidth")
                .define("hudWidth", 152);
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
                .define("autoConnectServerFilterType", AutoConnectServerFilterType.WHITE_LIST.name());
        autoConnectBlackList = builder
                .comment("Auto connecting servers black list")
                .translation(MusicHud.MOD_ID + ".config.autoConnectBlackList")
                .define("autoConnectBlackList", "[]");
        autoConnectWhiteList = builder
                .comment("Auto connecting servers white list")
                .translation(MusicHud.MOD_ID + ".config.autoConnectWhiteList")
                .define("autoConnectBlackList", "[]");
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
    public void setEnableAutoConnect(boolean autoConnect) {
        this.enableAutoConnect.set(autoConnect);
    }

    @Override
    public boolean getEnableIsolatedMode() {
        return this.enableIsolatedMode.get();
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
    public void setConnectServerFilterType(AutoConnectServerFilterType type) {
        autoConnectServerFilterType.set(type.name());
    }

    @Override
    public List<String> getBlackList() {
        //noinspection unchecked
        return JsonUtil.gson.fromJson(autoConnectBlackList.get(), List.class);
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
    public boolean getShowTranslatedCnLyrics() {
        return showTranslatedCnLyrics.get();
    }

    @Override
    public void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics) {
        this.showTranslatedCnLyrics.set(showTranslatedCnLyrics);
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
    public boolean getHideHudWhenNotPlaying() {
        return hideHudWhenNotPlaying.get();
    }

    @Override
    public void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying) {
        this.hideHudWhenNotPlaying.set(hideHudWhenNotPlaying);
    }

    @Override
    public boolean getEnableHud() {
        return enableHud.get();
    }

    @Override
    public void setEnableHud(boolean enableHud) {
        this.enableHud.set(enableHud);
    }

    @Override
    public Quality getPrimaryChosenQuality() {
        return Quality.valueOf(primaryChosenQuality.get());
    }

    @Override
    public void setPrimaryChosenQuality(Quality primaryChosenQuality) {
        this.primaryChosenQuality.set(primaryChosenQuality.name());
    }

    @Override
    public VerticalAlign getHudVerticalPosition() {
        return VerticalAlign.valueOf(hudVerticalPosition.get());
    }

    @Override
    public void setHudVerticalPosition(VerticalAlign hudVerticalPosition) {
        this.hudVerticalPosition.set(hudVerticalPosition.name());
    }

    @Override
    public HorizontalAlign getHudHorizontalPosition() {
        return HorizontalAlign.valueOf(hudHorizontalPosition.get());
    }

    @Override
    public void setHudHorizontalPosition(HorizontalAlign hudHorizontalPosition) {
        this.hudHorizontalPosition.set(hudHorizontalPosition.name());
    }

    @Override
    public int getHudOffsetX() {
        return hudOffsetX.get();
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
    public void setHudOffsetY(int hudOffsetY) {
        this.hudOffsetY.set(hudOffsetY);
    }

    @Override
    public int getHudWidth() {
        return hudWidth.get();
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
    public void setHudHeight(int hudHeight) {
        this.hudHeight.set(hudHeight);
    }

    @Override
    public int getHudCornerRadius() {
        return hudCornerRadius.get();
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
    public void setEnabledInIntegratedServer(boolean enabledInIntegratedServer) {
        this.enabledInIntegratedServer.set(enabledInIntegratedServer);
    }

    @Override
    public void save() {
        configure.getRight().save();
    }

    @Override
    public double getMainScreenAdditionalBackgroundDarken() {
        return mainScreenAdditionalBackgroundDarken.get();
    }

    @Override
    public void setMainScreenAdditionalBackgroundDarken(double additionalBackgroundDarken) {
        mainScreenAdditionalBackgroundDarken.set(additionalBackgroundDarken);
    }

    @Override
    public boolean getMixWithVanillaSoundVolume() {
        return mixWithVanillaSoundVolume.get();
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
    public void setSoundVolumeInterval(int soundVolume) {
        this.soundVolumeInterval.set(soundVolume);
    }

    @Override
    public boolean getEnableMarqueeText() {
        return enableMarqueeText.get();
    }

    @Override
    public void setEnableMarqueeText(boolean enable) {
        enableMarqueeText.set(enable);
    }
}