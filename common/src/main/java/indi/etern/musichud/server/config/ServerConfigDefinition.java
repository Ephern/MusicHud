package indi.etern.musichud.server.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.server.api.ServerApiMeta;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfigDefinition {
    public static final Pair<ServerConfigDefinition, ModConfigSpec> configure;
    public final ModConfigSpec.ConfigValue<String> serverApiBaseUrl;
    public final ModConfigSpec.ConfigValue<Double> pusherVoteAdditionalRate;
    public final ModConfigSpec.ConfigValue<Boolean> useRandomCnIp;
    public ServerConfigDefinition(ModConfigSpec.Builder builder) {
        serverApiBaseUrl = builder
                .comment("Server API Base URL configuration")
                .translation(MusicHud.MOD_ID + ".serverApiBaseUrl")
                .define("serverApiBaseUrl", ServerApiMeta.DEFAULT_API_BASE_URL);
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
