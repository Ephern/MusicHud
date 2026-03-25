package indi.etern.musichud.client.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ClientConfigDefinition {
    public static boolean configured = false;
    public static Pair<ClientConfigDefinition, ModConfigSpec> configure;
    public static ModConfigSpec.ConfigValue<Boolean> enable;
    public static ModConfigSpec.ConfigValue<Boolean> showTranslatedCnLyrics;
    public static ModConfigSpec.ConfigValue<Boolean> disableVanillaMusic;
    public static ModConfigSpec.ConfigValue<Boolean> hideHudWhenNotPlaying;
    public static ModConfigSpec.ConfigValue<Boolean> enableHud;
    public static ModConfigSpec.ConfigValue<String> primaryChosenQuality;
    public static ModConfigSpec.ConfigValue<String> hudVerticalPosition;
    public static ModConfigSpec.ConfigValue<String> hudHorizontalPosition;
    public static ModConfigSpec.ConfigValue<Integer> hudOffsetX;
    public static ModConfigSpec.ConfigValue<Integer> hudOffsetY;
    public static ModConfigSpec.ConfigValue<Integer> hudWidth;
    public static ModConfigSpec.ConfigValue<Integer> hudHeight;
    public static ModConfigSpec.ConfigValue<Integer> hudCornerRadius;
    public static ModConfigSpec.ConfigValue<String> clientCookie;
    public static ModConfigSpec.ConfigValue<String> clientAccountConfig;
    public static ModConfigSpec.ConfigValue<Boolean> enableEmbeddedServer;
    public static final String ENABLE_KEY = MusicHud.MOD_ID + ".config.common.enable";
    public static final String SHOW_TRANSLATED_CN_LYRICS = MusicHud.MOD_ID + ".config.common.showTranslatedCnLyrics";
    public static final String DISABLE_VANILLA_MUSIC_KEY = MusicHud.MOD_ID + ".config.commmon.disableVanillaMusic";
    public static final String HIDE_HUD_WHEN_NOT_PLAYING_KEY = MusicHud.MOD_ID + ".config.common.enableHud";
    public static final String ENABLE_HUD_KEY = MusicHud.MOD_ID + ".config.common.hud.enable";
    public static final String PRIMARY_CHOSEN_QUALITY_KEY = MusicHud.MOD_ID + ".config.common.primaryChosenQuality";
    public static final String VERTICAL_POSITION_KEY = MusicHud.MOD_ID + ".config.layout.verticalAlign";
    public static final String HORIZONTAL_POSITION_KEY = MusicHud.MOD_ID + ".config.layout.horizontalAlign";
    public static final String OFFSET_X_KEY = MusicHud.MOD_ID + ".config.layout.offsetX";
    public static final String OFFSET_Y_KEY = MusicHud.MOD_ID + ".config.layout.offsetY";
    public static final String HUD_WIDTH_KEY = MusicHud.MOD_ID + ".config.layout.hudWidth";
    public static final String HUD_HEIGHT_KEY = MusicHud.MOD_ID + ".config.layout.hudHeight";
    public static final String HUD_CORNER_RADIUS_KEY = MusicHud.MOD_ID + ".config.layout.hudCornerRadius";
    public static final String CLIENT_COOKIE_KEY = MusicHud.MOD_ID + ".internal.clientCookie";
    public static final String CLIENT_ACCOUNT_CONFIG = MusicHud.MOD_ID + ".internal.clientAccountConfig";
    public static final String ENABLE_EMBEDDED_SERVER_KEY = MusicHud.MOD_ID + ".config.embeddedServer.enable";

    ClientConfigDefinition(ModConfigSpec.Builder builder) {
        enable = builder
                .comment("Enable Music Hud Functions")
                .translation(ENABLE_KEY)
                .define("enable", true);
        showTranslatedCnLyrics = builder
                .comment("Show translated Chinese lyrics")
                .translation(SHOW_TRANSLATED_CN_LYRICS)
                .define("showTranslatedCnLyrics", true);
        disableVanillaMusic = builder
                .comment("Disable vanilla game music")
                .translation(DISABLE_VANILLA_MUSIC_KEY)
                .define("disableVanillaMusic", true);
        hideHudWhenNotPlaying = builder
                .comment("Hide hud when not playing music")
                .translation(HIDE_HUD_WHEN_NOT_PLAYING_KEY)
                .define("hideHudWhenNotPlaying", true);
        enableHud = builder
                .comment("Enable hud")
                .translation(ENABLE_HUD_KEY)
                .define("enableHud", true);
        primaryChosenQuality = builder
                .comment("Primary chosen quality")
                .translation(PRIMARY_CHOSEN_QUALITY_KEY)
                .define("primaryChosenQuality", Quality.LOSSLESS.name());
        hudVerticalPosition = builder
                .comment("Vertical position (TOP|CENTER|BOTTOM)")
                .translation(VERTICAL_POSITION_KEY)
                .define("verticalPosition", VerticalAlign.TOP.name());
        hudHorizontalPosition = builder
                .comment("Horizontal position (LEFT|CENTER|RIGHT)")
                .translation(HORIZONTAL_POSITION_KEY)
                .define("horizontalPosition", HorizontalAlign.LEFT.name());
        hudOffsetX = builder
                .comment("Hud offset x")
                .translation(OFFSET_X_KEY)
                .define("hudOffsetX", 16);
        hudOffsetY = builder
                .comment("Hud offset y")
                .translation(OFFSET_Y_KEY)
                .define("hudOffsetY", 16);
        hudWidth = builder
                .comment("Hud width")
                .translation(HUD_WIDTH_KEY)
                .define("hudWidth", 150);
        hudHeight = builder
                .comment("Hud height")
                .translation(HUD_HEIGHT_KEY)
                .define("hudHeight", 44);
        hudCornerRadius = builder
                .comment("Hud rounded corner radius")
                .translation(HUD_CORNER_RADIUS_KEY)
                .define("hudCornerRadius", 8);
        clientCookie = builder
                .comment("Client NCM cookie json")
                .translation(CLIENT_COOKIE_KEY)
                .define("clientCookie", "");
        clientAccountConfig = builder
                .comment("Client account config json")
                .translation(CLIENT_ACCOUNT_CONFIG)
                .define("clientAccountConfig", "");
        enableEmbeddedServer = builder
                .comment("Enable embedded server (To enable Music HUD in singleplayer or LAN multiplayer)")
                .translation(ENABLE_EMBEDDED_SERVER_KEY)
                .define("enableEmbeddedServer", true);
    }

    public static void configure() {
        if (configure == null) {
            configure = new ModConfigSpec.Builder().configure(ClientConfigDefinition::new);
        }
    }

    static {
        configure();
    }
}