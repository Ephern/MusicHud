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
    private final ModConfigSpec.ConfigValue<String> corsAllowOrigin;
    private final ModConfigSpec.ConfigValue<Boolean> enableProxy;
    private final ModConfigSpec.ConfigValue<String> proxyUrl;
    private final ModConfigSpec.ConfigValue<Boolean> enableGeneralUnblock;
    private final ModConfigSpec.ConfigValue<Boolean> enableFlac;
    private final ModConfigSpec.ConfigValue<Boolean> selectMaxBr;
    private final ModConfigSpec.ConfigValue<Boolean> followSourceOrder;
    private final ModConfigSpec.ConfigValue<Integer> port;
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
        corsAllowOrigin = builder
                .comment("CORS allow origin for the API server (single or comma-separated origins)")
                .translation(MusicHud.MOD_ID + ".corsAllowOrigin")
                .define("corsAllowOrigin", "*");
        enableProxy = builder
                .comment("Enable reverse proxy function of the API server")
                .translation(MusicHud.MOD_ID + ".enableProxy")
                .define("enableProxy", false);
        proxyUrl = builder
                .comment("Proxy server URL. Only effective when enableProxy is true")
                .translation(MusicHud.MOD_ID + ".proxyUrl")
                .define("proxyUrl", "https://your-proxy-url.com/?proxy=");
        enableGeneralUnblock = builder
                .comment("Enable global unblock (recommended)")
                .translation(MusicHud.MOD_ID + ".enableGeneralUnblock")
                .define("enableGeneralUnblock", true);
        enableFlac = builder
                .comment("Enable FLAC lossless quality")
                .translation(MusicHud.MOD_ID + ".enableFlac")
                .define("enableFlac", true);
        selectMaxBr = builder
                .comment("Select highest bitrate when lossless quality is enabled")
                .translation(MusicHud.MOD_ID + ".selectMaxBr")
                .define("selectMaxBr", false);
        followSourceOrder = builder
                .comment("Match sources strictly in order of the source list")
                .translation(MusicHud.MOD_ID + ".followSourceOrder")
                .define("followSourceOrder", true);
        port = builder
                .comment("Port for the API server to listen on")
                .translation(MusicHud.MOD_ID + ".port")
                .defineInRange("port", 3000, 1, 65535);
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
    public String getCorsAllowOrigin() {
        return corsAllowOrigin.get();
    }

    @Override
    public void setCorsAllowOrigin(String corsAllowOrigin) {
        this.corsAllowOrigin.set(corsAllowOrigin);
    }

    @Override
    public boolean getEnableProxy() {
        return enableProxy.get();
    }

    @Override
    public void setEnableProxy(boolean enableProxy) {
        this.enableProxy.set(enableProxy);
    }

    @Override
    public String getProxyUrl() {
        return proxyUrl.get();
    }

    @Override
    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl.set(proxyUrl);
    }

    @Override
    public boolean getEnableGeneralUnblock() {
        return enableGeneralUnblock.get();
    }

    @Override
    public void setEnableGeneralUnblock(boolean enableGeneralUnblock) {
        this.enableGeneralUnblock.set(enableGeneralUnblock);
    }

    @Override
    public boolean getEnableFlac() {
        return enableFlac.get();
    }

    @Override
    public void setEnableFlac(boolean enableFlac) {
        this.enableFlac.set(enableFlac);
    }

    @Override
    public boolean getSelectMaxBr() {
        return selectMaxBr.get();
    }

    @Override
    public void setSelectMaxBr(boolean selectMaxBr) {
        this.selectMaxBr.set(selectMaxBr);
    }

    @Override
    public boolean getFollowSourceOrder() {
        return followSourceOrder.get();
    }

    @Override
    public void setFollowSourceOrder(boolean followSourceOrder) {
        this.followSourceOrder.set(followSourceOrder);
    }

    @Override
    public int getPort() {
        return port.get();
    }

    @Override
    public void setPort(int port) {
        this.port.set(port);
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