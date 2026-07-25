package indi.etern.musichud.platform.plugin.paper.config;

import indi.etern.musichud.interfaces.ServerConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class ServerConfigDefinition implements ServerConfig {
    private static final String KEY_SERVER_API_BASE_URL = "serverApiBaseUrl";
    private static final String KEY_STARTUP_BINARY_API_SERVER = "startupBinaryApiServerWhenLaunch";
    private static final String KEY_SERVER_API_BINARY_EXECUTABLE_PATH = "serverApiBinaryExecutablePath";
    private static final String KEY_PUSHER_VOTE_ADDITIONAL_RATE = "pusherVoteAdditionalRate";
    private static final String KEY_USE_RANDOM_CN_IP = "useRandomCnIp";
    private static final String KEY_CORS_ALLOW_ORIGIN = "corsAllowOrigin";
    private static final String KEY_ENABLE_PROXY = "enableProxy";
    private static final String KEY_PROXY_URL = "proxyUrl";
    private static final String KEY_ENABLE_GENERAL_UNBLOCK = "enableGeneralUnblock";
    private static final String KEY_ENABLE_FLAC = "enableFlac";
    private static final String KEY_SELECT_MAX_BR = "selectMaxBr";
    private static final String KEY_FOLLOW_SOURCE_ORDER = "followSourceOrder";
    private static final String KEY_PORT = "port";

    private static final String DEFAULT_SERVER_API_BASE_URL = "http://localhost:3000";
    private static final boolean DEFAULT_STARTUP_BINARY_API_SERVER = true;
    private static final String LEGACY_SERVER_API_BINARY_EXECUTABLE_PATH = "music-hud/api";
    private static final String DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH = "api";
    private static final double DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE = 0.5D;
    private static final boolean DEFAULT_USE_RANDOM_CN_IP = true;
    private static final String DEFAULT_CORS_ALLOW_ORIGIN = "*";
    private static final boolean DEFAULT_ENABLE_PROXY = false;
    private static final String DEFAULT_PROXY_URL = "https://your-proxy-url.com/?proxy=";
    private static final boolean DEFAULT_ENABLE_GENERAL_UNBLOCK = true;
    private static final boolean DEFAULT_ENABLE_FLAC = true;
    private static final boolean DEFAULT_SELECT_MAX_BR = false;
    private static final boolean DEFAULT_FOLLOW_SOURCE_ORDER = true;
    private static final int DEFAULT_PORT = 3000;

    private static final ServerConfigDefinition INSTANCE = new ServerConfigDefinition();

    private JavaPlugin plugin;
    private String serverApiBaseUrl = DEFAULT_SERVER_API_BASE_URL;
    private boolean startupBinaryApiServerWhenLaunch = DEFAULT_STARTUP_BINARY_API_SERVER;
    private String serverApiBinaryExecutablePath = DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH;
    private double pusherVoteAdditionalRate = DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE;
    private boolean useRandomCnIp = DEFAULT_USE_RANDOM_CN_IP;
    private String corsAllowOrigin = DEFAULT_CORS_ALLOW_ORIGIN;
    private boolean enableProxy = DEFAULT_ENABLE_PROXY;
    private String proxyUrl = DEFAULT_PROXY_URL;
    private boolean enableGeneralUnblock = DEFAULT_ENABLE_GENERAL_UNBLOCK;
    private boolean enableFlac = DEFAULT_ENABLE_FLAC;
    private boolean selectMaxBr = DEFAULT_SELECT_MAX_BR;
    private boolean followSourceOrder = DEFAULT_FOLLOW_SOURCE_ORDER;
    private int port = DEFAULT_PORT;
    private boolean configured;

    private ServerConfigDefinition() {
    }

    public static ServerConfigDefinition getInstance() {
        return INSTANCE;
    }

    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        boolean changed = applyDefaults(config);
        load(config);
        configured = true;
        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean applyDefaults(FileConfiguration config) {
        boolean changed = false;
        changed |= ensureDefault(config, KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
        changed |= ensureDefault(config, KEY_STARTUP_BINARY_API_SERVER, DEFAULT_STARTUP_BINARY_API_SERVER);
        changed |= ensureDefault(config, KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
        changed |= ensureDefault(config, KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE);
        changed |= ensureDefault(config, KEY_USE_RANDOM_CN_IP, DEFAULT_USE_RANDOM_CN_IP);
        changed |= ensureDefault(config, KEY_CORS_ALLOW_ORIGIN, DEFAULT_CORS_ALLOW_ORIGIN);
        changed |= ensureDefault(config, KEY_ENABLE_PROXY, DEFAULT_ENABLE_PROXY);
        changed |= ensureDefault(config, KEY_PROXY_URL, DEFAULT_PROXY_URL);
        changed |= ensureDefault(config, KEY_ENABLE_GENERAL_UNBLOCK, DEFAULT_ENABLE_GENERAL_UNBLOCK);
        changed |= ensureDefault(config, KEY_ENABLE_FLAC, DEFAULT_ENABLE_FLAC);
        changed |= ensureDefault(config, KEY_SELECT_MAX_BR, DEFAULT_SELECT_MAX_BR);
        changed |= ensureDefault(config, KEY_FOLLOW_SOURCE_ORDER, DEFAULT_FOLLOW_SOURCE_ORDER);
        changed |= ensureDefault(config, KEY_PORT, DEFAULT_PORT);
        changed |= migrateLegacyBinaryExecutablePath(config);
        return changed;
    }

    private boolean ensureDefault(FileConfiguration config, String key, Object value) {
        if (!config.contains(key)) {
            config.set(key, value);
            return true;
        }
        return false;
    }

    private boolean migrateLegacyBinaryExecutablePath(FileConfiguration config) {
        String configuredPath = config.getString(KEY_SERVER_API_BINARY_EXECUTABLE_PATH);
        if (LEGACY_SERVER_API_BINARY_EXECUTABLE_PATH.equals(configuredPath)) {
            config.set(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
            return true;
        }
        return false;
    }

    private void load(FileConfiguration config) {
        serverApiBaseUrl = config.getString(KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
        startupBinaryApiServerWhenLaunch = config.getBoolean(KEY_STARTUP_BINARY_API_SERVER, DEFAULT_STARTUP_BINARY_API_SERVER);
        serverApiBinaryExecutablePath = config.getString(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
        pusherVoteAdditionalRate = clampRate(config.getDouble(KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE));
        useRandomCnIp = config.getBoolean(KEY_USE_RANDOM_CN_IP, DEFAULT_USE_RANDOM_CN_IP);
        corsAllowOrigin = config.getString(KEY_CORS_ALLOW_ORIGIN, DEFAULT_CORS_ALLOW_ORIGIN);
        enableProxy = config.getBoolean(KEY_ENABLE_PROXY, DEFAULT_ENABLE_PROXY);
        proxyUrl = config.getString(KEY_PROXY_URL, DEFAULT_PROXY_URL);
        enableGeneralUnblock = config.getBoolean(KEY_ENABLE_GENERAL_UNBLOCK, DEFAULT_ENABLE_GENERAL_UNBLOCK);
        enableFlac = config.getBoolean(KEY_ENABLE_FLAC, DEFAULT_ENABLE_FLAC);
        selectMaxBr = config.getBoolean(KEY_SELECT_MAX_BR, DEFAULT_SELECT_MAX_BR);
        followSourceOrder = config.getBoolean(KEY_FOLLOW_SOURCE_ORDER, DEFAULT_FOLLOW_SOURCE_ORDER);
        port = config.getInt(KEY_PORT, DEFAULT_PORT);
    }

    private double clampRate(double rate) {
        return Math.clamp(rate, 0.0D, 1.0D);
    }

    private FileConfiguration requireConfig() {
        return Objects.requireNonNull(plugin, "Paper server config is not initialized").getConfig();
    }

    @Override
    public void setServerApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl;
    }

    @Override
    public void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch) {
        this.startupBinaryApiServerWhenLaunch = startupBinaryApiServerWhenLaunch;
    }

    @Override
    public void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath) {
        this.serverApiBinaryExecutablePath = serverApiBinaryExecutablePath;
    }

    @Override
    public void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate) {
        this.pusherVoteAdditionalRate = clampRate(pusherVoteAdditionalRate);
    }

    @Override
    public void setUseRandomCnIp(boolean useRandomCnIp) {
        this.useRandomCnIp = useRandomCnIp;
    }

    @Override
    public String getServerApiBaseUrl() {
        return serverApiBaseUrl;
    }

    @Override
    public boolean getStartupBinaryApiServerWhenLaunch() {
        return startupBinaryApiServerWhenLaunch;
    }

    @Override
    public String getServerApiBinaryExecutablePath() {
        Path configuredPath = Paths.get(serverApiBinaryExecutablePath);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize().toString();
        }
        JavaPlugin initializedPlugin = Objects.requireNonNull(plugin, "Paper server config is not initialized");
        return initializedPlugin.getDataFolder().toPath().resolve(configuredPath).normalize().toString();
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate;
    }

    @Override
    public boolean getUseRandomCnIp() {
        return useRandomCnIp;
    }

    @Override
    public String getCorsAllowOrigin() {
        return corsAllowOrigin;
    }

    @Override
    public void setCorsAllowOrigin(String corsAllowOrigin) {
        this.corsAllowOrigin = corsAllowOrigin;
    }

    @Override
    public boolean getEnableProxy() {
        return enableProxy;
    }

    @Override
    public void setEnableProxy(boolean enableProxy) {
        this.enableProxy = enableProxy;
    }

    @Override
    public String getProxyUrl() {
        return proxyUrl;
    }

    @Override
    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public boolean getEnableGeneralUnblock() {
        return enableGeneralUnblock;
    }

    @Override
    public void setEnableGeneralUnblock(boolean enableGeneralUnblock) {
        this.enableGeneralUnblock = enableGeneralUnblock;
    }

    @Override
    public boolean getEnableFlac() {
        return enableFlac;
    }

    @Override
    public void setEnableFlac(boolean enableFlac) {
        this.enableFlac = enableFlac;
    }

    @Override
    public boolean getSelectMaxBr() {
        return selectMaxBr;
    }

    @Override
    public void setSelectMaxBr(boolean selectMaxBr) {
        this.selectMaxBr = selectMaxBr;
    }

    @Override
    public boolean getFollowSourceOrder() {
        return followSourceOrder;
    }

    @Override
    public void setFollowSourceOrder(boolean followSourceOrder) {
        this.followSourceOrder = followSourceOrder;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public void save() {
        FileConfiguration config = requireConfig();
        config.set(KEY_SERVER_API_BASE_URL, serverApiBaseUrl);
        config.set(KEY_STARTUP_BINARY_API_SERVER, startupBinaryApiServerWhenLaunch);
        config.set(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, serverApiBinaryExecutablePath);
        config.set(KEY_PUSHER_VOTE_ADDITIONAL_RATE, pusherVoteAdditionalRate);
        config.set(KEY_USE_RANDOM_CN_IP, useRandomCnIp);
        config.set(KEY_CORS_ALLOW_ORIGIN, corsAllowOrigin);
        config.set(KEY_ENABLE_PROXY, enableProxy);
        config.set(KEY_PROXY_URL, proxyUrl);
        config.set(KEY_ENABLE_GENERAL_UNBLOCK, enableGeneralUnblock);
        config.set(KEY_ENABLE_FLAC, enableFlac);
        config.set(KEY_SELECT_MAX_BR, selectMaxBr);
        config.set(KEY_FOLLOW_SOURCE_ORDER, followSourceOrder);
        config.set(KEY_PORT, port);
        plugin.saveConfig();
    }

    @Override
    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }
}
