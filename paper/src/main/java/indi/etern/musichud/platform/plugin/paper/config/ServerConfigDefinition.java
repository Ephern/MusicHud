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

    private static final String DEFAULT_SERVER_API_BASE_URL = "http://localhost:3000";
    private static final boolean DEFAULT_STARTUP_BINARY_API_SERVER = true;
    private static final String LEGACY_SERVER_API_BINARY_EXECUTABLE_PATH = "music-hud/api";
    private static final String DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH = "api";
    private static final double DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE = 0.5D;
    private static final boolean DEFAULT_USE_RANDOM_CN_IP = true;

    private static final ServerConfigDefinition INSTANCE = new ServerConfigDefinition();

    private JavaPlugin plugin;
    private String serverApiBaseUrl = DEFAULT_SERVER_API_BASE_URL;
    private boolean startupBinaryApiServerWhenLaunch = DEFAULT_STARTUP_BINARY_API_SERVER;
    private String serverApiBinaryExecutablePath = DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH;
    private double pusherVoteAdditionalRate = DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE;
    private boolean useRandomCnIp = DEFAULT_USE_RANDOM_CN_IP;
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
    }

    private double clampRate(double rate) {
        return Math.max(0.0D, Math.min(1.0D, rate));
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
    public void save() {
        FileConfiguration config = requireConfig();
        config.set(KEY_SERVER_API_BASE_URL, serverApiBaseUrl);
        config.set(KEY_STARTUP_BINARY_API_SERVER, startupBinaryApiServerWhenLaunch);
        config.set(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, serverApiBinaryExecutablePath);
        config.set(KEY_PUSHER_VOTE_ADDITIONAL_RATE, pusherVoteAdditionalRate);
        config.set(KEY_USE_RANDOM_CN_IP, useRandomCnIp);
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
