package indi.etern.musichud.platform.mod.forgeConfig.config;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ServerConfig;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfigDefinition implements ServerConfig {
    public static final Pair<ServerConfigDefinition, ModConfigSpec> configure;
    @Getter
    private static ServerConfigDefinition instance;
    private final ModConfigSpec.ConfigValue<String> serverApiBaseUrl;
    private final ModConfigSpec.ConfigValue<Boolean> startupBinaryApiServerWhenLaunch;
    private final ModConfigSpec.ConfigValue<String> serverApiBinaryExecutablePath;
    private final ModConfigSpec.ConfigValue<Double> pusherVoteAdditionalRate;
    private final ModConfigSpec.ConfigValue<Boolean> useRandomCnIp;
    @Setter
    @Getter
    private boolean configured;

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
                .translation(MusicHud.MOD_ID + ".pusherVoteAdditionalRate")
                .defineInRange("pusherVoteAdditionalRate", 0.5, 0, 1);
        useRandomCnIp = builder
                .comment("Use random Chinese IP provided by api server")
                .translation(MusicHud.MOD_ID + ".useRandomCnIp")
                .define("useRandomCnIp", true);
        instance = this;
    }

    static {
         configure = new ModConfigSpec.Builder().configure(ServerConfigDefinition::new);
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl.set(serverApiBaseUrl);
    }

    @Override
    public void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch) {
        this.startupBinaryApiServerWhenLaunch.set(startupBinaryApiServerWhenLaunch);
    }

    @Override
    public void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath) {
        this.serverApiBinaryExecutablePath.set(serverApiBinaryExecutablePath);
    }

    @Override
    public void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate) {
        this.pusherVoteAdditionalRate.set(pusherVoteAdditionalRate);
    }

    @Override
    public void setUseRandomCnIp(boolean useRandomCnIp) {
        this.useRandomCnIp.set(useRandomCnIp);
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl.get();
    }

    @Override
    public boolean getStartupBinaryApiServerWhenLaunch() {
        return startupBinaryApiServerWhenLaunch.get();
    }

    @Override
    public String getServerApiBinaryExecutablePath() {
        return serverApiBinaryExecutablePath.get();
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate.get();
    }

    @Override
    public boolean getUseRandomCnIp() {
        return useRandomCnIp.get();
    }

    @Override
    public void save() {
        configure.getRight().save();
    }
}