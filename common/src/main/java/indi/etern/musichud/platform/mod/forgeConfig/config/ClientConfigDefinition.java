package indi.etern.musichud.platform.mod.forgeConfig.config;

import indi.etern.musichud.MusicHud;
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

public class ClientConfigDefinition implements ClientConfig {
    public static Pair<ClientConfigDefinition, ModConfigSpec> configure;
    @Getter
    private static ClientConfigDefinition instance;
    private final ModConfigSpec.ConfigValue<Boolean> enable;
    private final ModConfigSpec.ConfigValue<Boolean> showTranslatedCnLyrics;
    private final ModConfigSpec.ConfigValue<Boolean> disableVanillaMusic;
    private final ModConfigSpec.ConfigValue<Boolean> hideHudWhenNotPlaying;
    private final ModConfigSpec.ConfigValue<Boolean> enableHud;
    private final ModConfigSpec.ConfigValue<String> primaryChosenQuality;
    private final ModConfigSpec.ConfigValue<String> hudVerticalPosition;
    private final ModConfigSpec.ConfigValue<String> hudHorizontalPosition;
    private final ModConfigSpec.ConfigValue<Integer> hudOffsetX;
    private final ModConfigSpec.ConfigValue<Integer> hudOffsetY;
    private final ModConfigSpec.ConfigValue<Integer> hudWidth;
    private final ModConfigSpec.ConfigValue<Integer> hudHeight;
    private final ModConfigSpec.ConfigValue<Integer> hudCornerRadius;
    private final ModConfigSpec.ConfigValue<String> clientCookie;
    private final ModConfigSpec.ConfigValue<String> clientAccountConfig;
    private final ModConfigSpec.ConfigValue<Boolean> enableEmbeddedServer;
    @Setter
    @Getter
    private boolean configured;

    ClientConfigDefinition(ModConfigSpec.Builder builder) {
        enable = builder
                .comment("Enable Music Hud Functions")
                .translation(MusicHud.MOD_ID + ".config.common.enable")
                .define("enable", true);
        showTranslatedCnLyrics = builder
                .comment("Show translated Chinese lyrics")
                .translation(MusicHud.MOD_ID + ".config.common.showTranslatedCnLyrics")
                .define("showTranslatedCnLyrics", true);
        disableVanillaMusic = builder
                .comment("Disable vanilla game music")
                .translation(MusicHud.MOD_ID + ".config.commmon.disableVanillaMusic")
                .define("disableVanillaMusic", true);
        hideHudWhenNotPlaying = builder
                .comment("Hide hud when not playing music")
                .translation(MusicHud.MOD_ID + ".config.common.enableHud")
                .define("hideHudWhenNotPlaying", true);
        enableHud = builder
                .comment("Enable hud")
                .translation(MusicHud.MOD_ID + ".config.common.hud.enable")
                .define("enableHud", true);
        primaryChosenQuality = builder
                .comment("Primary chosen quality")
                .translation(MusicHud.MOD_ID + ".config.common.primaryChosenQuality")
                .define("primaryChosenQuality", Quality.LOSSLESS.name());
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
        enableEmbeddedServer = builder
                .comment("Enable embedded server (To enable Music HUD in singleplayer or LAN multiplayer)")
                .translation(MusicHud.MOD_ID + ".config.embeddedServer.enable")
                .define("enableEmbeddedServer", true);
        instance = this;
    }

    public static void configure() {
        if (configure == null) {
            configure = new ModConfigSpec.Builder().configure(ClientConfigDefinition::new);
        }
    }

    static {
        configure();
    }

    @Override
    public void setEnable(boolean enable) {
        this.enable.set(enable);
    }

    @Override
    public void setShowTranslatedCnLyrics(boolean showTranslatedCnLyrics) {
        this.showTranslatedCnLyrics.set(showTranslatedCnLyrics);
    }

    @Override
    public void setDisableVanillaMusic(boolean disableVanillaMusic) {
        this.disableVanillaMusic.set(disableVanillaMusic);
    }

    @Override
    public void setHideHudWhenNotPlaying(boolean hideHudWhenNotPlaying) {
        this.hideHudWhenNotPlaying.set(hideHudWhenNotPlaying);
    }

    @Override
    public void setEnableHud(boolean enableHud) {
        this.enableHud.set(enableHud);
    }

    @Override
    public void setPrimaryChosenQuality(Quality primaryChosenQuality) {
        this.primaryChosenQuality.set(primaryChosenQuality.name());
    }

    @Override
    public void setHudVerticalPosition(VerticalAlign hudVerticalPosition) {
        this.hudVerticalPosition.set(hudVerticalPosition.name());
    }

    @Override
    public void setHudHorizontalPosition(HorizontalAlign hudHorizontalPosition) {
        this.hudHorizontalPosition.set(hudHorizontalPosition.name());
    }

    @Override
    public void setHudOffsetX(int hudOffsetX) {
        this.hudOffsetX.set(hudOffsetX);
    }

    @Override
    public void setHudOffsetY(int hudOffsetY) {
        this.hudOffsetY.set(hudOffsetY);
    }

    @Override
    public void setHudWidth(int hudWidth) {
        this.hudWidth.set(hudWidth);
    }

    @Override
    public void setHudHeight(int hudHeight) {
        this.hudHeight.set(hudHeight);
    }

    @Override
    public void setHudCornerRadius(int hudCornerRadius) {
        this.hudCornerRadius.set(hudCornerRadius);
    }

    @Override
    public void setClientCookie(LoginCookieInfo clientCookie) {
        this.clientCookie.set(JsonUtil.gson.toJson(clientCookie));
    }

    @Override
    public void setClientAccountConfig(ProfileConfigData clientAccountConfig) {
        this.clientAccountConfig.set(JsonUtil.gson.toJson(clientAccountConfig));
    }

    @Override
    public void setEnableEmbeddedServer(boolean enableEmbeddedServer) {
        this.enableEmbeddedServer.set(enableEmbeddedServer);
    }

    @Override
    public boolean getEnable() {
        return enable.get();
    }

    @Override
    public boolean getShowTranslatedCnLyrics() {
        return showTranslatedCnLyrics.get();
    }

    @Override
    public boolean getDisableVanillaMusic() {
        return disableVanillaMusic.get();
    }

    @Override
    public boolean getHideHudWhenNotPlaying() {
        return hideHudWhenNotPlaying.get();
    }

    @Override
    public boolean getEnableHud() {
        return enableHud.get();
    }

    @Override
    public Quality getPrimaryChosenQuality() {
        return Quality.valueOf(primaryChosenQuality.get());
    }

    @Override
    public VerticalAlign getHudVerticalPosition() {
        return VerticalAlign.valueOf(hudVerticalPosition.get());
    }

    @Override
    public HorizontalAlign getHudHorizontalPosition() {
        return HorizontalAlign.valueOf(hudHorizontalPosition.get());
    }

    @Override
    public int getHudOffsetX() {
        return hudOffsetX.get();
    }

    @Override
    public int getHudOffsetY() {
        return hudOffsetY.get();
    }

    @Override
    public int getHudWidth() {
        return hudWidth.get();
    }

    @Override
    public int getHudHeight() {
        return hudHeight.get();
    }

    @Override
    public int getHudCornerRadius() {
        return hudCornerRadius.get();
    }

    @Override
    public LoginCookieInfo getClientCookie() {
        return JsonUtil.gson.fromJson(clientCookie.get(), LoginCookieInfo.class);
    }

    @Override
    public ProfileConfigData getClientAccountConfig() {
        return JsonUtil.gson.fromJson(clientAccountConfig.get(), ProfileConfigData.class);
    }

    @Override
    public boolean getEnableEmbeddedServer() {
        return enableEmbeddedServer.get();
    }

    @Override
    public void save() {
        configure.getRight().save();
    }
}