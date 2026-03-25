package indi.etern.musichud.server.config;

import indi.etern.musichud.MusicHud;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfigDefinition {
    public static boolean configured = false;
    public static final Pair<ServerConfigDefinition, ModConfigSpec> configure;
    public static ModConfigSpec.ConfigValue<String> serverApiBaseUrl;
    public static ModConfigSpec.ConfigValue<Boolean> startupBinaryApiServerWhenLaunch;
    public static ModConfigSpec.ConfigValue<String> serverApiBinaryExecutablePath;
    public static ModConfigSpec.ConfigValue<Double> pusherVoteAdditionalRate;
    public static ModConfigSpec.ConfigValue<Boolean> useRandomCnIp;
    public ServerConfigDefinition(ModConfigSpec.Builder builder) {
        serverApiBaseUrl = builder
                .comment("Server API base URL configuration")
                .translation(MusicHud.MOD_ID + ".serverApiBaseUrl")
                .define("serverApiBaseUrl", "http://localhost:3000");
        startupBinaryApiServerWhenLaunch = builder
                .comment("Startup binary api server when game launch")
                .translation(MusicHud.MOD_ID + ".startupBinaryApiServerWhenLaunch")
                .define("startupBinaryApiServerWhenLaunch", true);
        serverApiBinaryExecutablePath = builder
                .comment("Server API binary executable path (defaults relative to game version core jar path). If executable binary api is available, Music HUD will try to call it as sub-process")
                .translation(MusicHud.MOD_ID + ".serverApiBinaryExecutablePath")
                .define("serverApiBinaryExecutablePath", "music-hud/api");
        pusherVoteAdditionalRate = builder
                .comment("Music Pusher's vote additional rate when voting for skip music configuration (0.0 ~ 1.0, total rate larger than or equals to 0.5 means to skip)")
                .translation("music_hud.pusherVoteAdditionalRate")
                .defineInRange("pusherVoteAdditionalRate", 0.5, 0, 1);
        useRandomCnIp = builder
                .comment("Use random Chinese IP provided by api server")
                .translation("music_hud.useRandomCnIp")
                .define("useRandomCnIp", true);
    }

    static {
         configure = new ModConfigSpec.Builder().configure(ServerConfigDefinition::new);
    }
}
