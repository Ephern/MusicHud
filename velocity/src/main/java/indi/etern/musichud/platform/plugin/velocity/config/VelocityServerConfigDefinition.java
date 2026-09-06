package indi.etern.musichud.platform.plugin.velocity.config;

import indi.etern.musichud.interfaces.ServerConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class VelocityServerConfigDefinition implements ServerConfig {
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

    private static final VelocityServerConfigDefinition INSTANCE = new VelocityServerConfigDefinition();

    private final Yaml yaml = new Yaml(new DumperOptions() {{
        setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    }});
    private Path dataDirectory;
    private Path configPath;
    private Map<String, Object> config = new LinkedHashMap<>();
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

    private VelocityServerConfigDefinition() {
    }

    public static VelocityServerConfigDefinition getInstance() {
        return INSTANCE;
    }

    public void initialize(Path dataDirectory) {
        try {
            this.dataDirectory = dataDirectory;
            Files.createDirectories(dataDirectory);
            configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                copyDefaultConfig();
            }
            load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize MusicHud velocity config", e);
        }
    }

    private void copyDefaultConfig() throws IOException {
        try (InputStream inputStream = VelocityServerConfigDefinition.class.getResourceAsStream("/config.yml")) {
            if (inputStream == null) {
                Files.createFile(configPath);
                return;
            }
            Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void load() {
        Map<String, Object> loaded;
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Object parsed = yaml.load(inputStream);
            loaded = parsed instanceof Map<?, ?> map ? toStringKeyMap(map) : new LinkedHashMap<>();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read MusicHud velocity config", e);
        }
        config = loaded;
        boolean changed = applyDefaults();
        readValues();
        configured = true;
        if (changed) {
            save();
        }
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private boolean applyDefaults() {
        boolean changed = false;
        changed |= ensureDefault(KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
        changed |= ensureDefault(KEY_STARTUP_BINARY_API_SERVER, DEFAULT_STARTUP_BINARY_API_SERVER);
        changed |= ensureDefault(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
        changed |= ensureDefault(KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE);
        changed |= ensureDefault(KEY_USE_RANDOM_CN_IP, DEFAULT_USE_RANDOM_CN_IP);
        changed |= ensureDefault(KEY_CORS_ALLOW_ORIGIN, DEFAULT_CORS_ALLOW_ORIGIN);
        changed |= ensureDefault(KEY_ENABLE_PROXY, DEFAULT_ENABLE_PROXY);
        changed |= ensureDefault(KEY_PROXY_URL, DEFAULT_PROXY_URL);
        changed |= ensureDefault(KEY_ENABLE_GENERAL_UNBLOCK, DEFAULT_ENABLE_GENERAL_UNBLOCK);
        changed |= ensureDefault(KEY_ENABLE_FLAC, DEFAULT_ENABLE_FLAC);
        changed |= ensureDefault(KEY_SELECT_MAX_BR, DEFAULT_SELECT_MAX_BR);
        changed |= ensureDefault(KEY_FOLLOW_SOURCE_ORDER, DEFAULT_FOLLOW_SOURCE_ORDER);
        changed |= ensureDefault(KEY_PORT, DEFAULT_PORT);
        changed |= migrateLegacyBinaryExecutablePath();
        return changed;
    }

    private boolean ensureDefault(String key, Object value) {
        if (!config.containsKey(key)) {
            config.put(key, value);
            return true;
        }
        return false;
    }

    private boolean migrateLegacyBinaryExecutablePath() {
        Object configuredPath = config.get(KEY_SERVER_API_BINARY_EXECUTABLE_PATH);
        if (LEGACY_SERVER_API_BINARY_EXECUTABLE_PATH.equals(configuredPath)) {
            config.put(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
            return true;
        }
        return false;
    }

    private void readValues() {
        serverApiBaseUrl = getString(KEY_SERVER_API_BASE_URL, DEFAULT_SERVER_API_BASE_URL);
        startupBinaryApiServerWhenLaunch = getBoolean(KEY_STARTUP_BINARY_API_SERVER, DEFAULT_STARTUP_BINARY_API_SERVER);
        serverApiBinaryExecutablePath = getString(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH);
        pusherVoteAdditionalRate = clampRate(getDouble(KEY_PUSHER_VOTE_ADDITIONAL_RATE, DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE));
        useRandomCnIp = getBoolean(KEY_USE_RANDOM_CN_IP, DEFAULT_USE_RANDOM_CN_IP);
        corsAllowOrigin = getString(KEY_CORS_ALLOW_ORIGIN, DEFAULT_CORS_ALLOW_ORIGIN);
        enableProxy = getBoolean(KEY_ENABLE_PROXY, DEFAULT_ENABLE_PROXY);
        proxyUrl = getString(KEY_PROXY_URL, DEFAULT_PROXY_URL);
        enableGeneralUnblock = getBoolean(KEY_ENABLE_GENERAL_UNBLOCK, DEFAULT_ENABLE_GENERAL_UNBLOCK);
        enableFlac = getBoolean(KEY_ENABLE_FLAC, DEFAULT_ENABLE_FLAC);
        selectMaxBr = getBoolean(KEY_SELECT_MAX_BR, DEFAULT_SELECT_MAX_BR);
        followSourceOrder = getBoolean(KEY_FOLLOW_SOURCE_ORDER, DEFAULT_FOLLOW_SOURCE_ORDER);
        port = getInt(KEY_PORT, DEFAULT_PORT);
    }

    private String getString(String key, String defaultValue) {
        Object value = config.get(key);
        return value instanceof String string ? string : defaultValue;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private double getDouble(String key, double defaultValue) {
        Object value = config.get(key);
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private double clampRate(double rate) {
        return Math.clamp(rate, 0.0D, 1.0D);
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
    public String getDefaultServerApiBaseUrl() {
        return DEFAULT_SERVER_API_BASE_URL;
    }

    @Override
    public boolean getStartupBinaryApiServerWhenLaunch() {
        return startupBinaryApiServerWhenLaunch;
    }

    @Override
    public boolean getDefaultStartupBinaryApiServerWhenLaunch() {
        return DEFAULT_STARTUP_BINARY_API_SERVER;
    }

    @Override
    public String getServerApiBinaryExecutablePath() {
        Path configuredPath = Paths.get(serverApiBinaryExecutablePath);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize().toString();
        }
        Path base = Objects.requireNonNull(dataDirectory, "Velocity server config is not initialized");
        return base.resolve(configuredPath).normalize().toString();
    }

    @Override
    public String getDefaultServerApiBinaryExecutablePath() {
        return DEFAULT_SERVER_API_BINARY_EXECUTABLE_PATH;
    }

    @Override
    public double getPusherVoteAdditionalRate() {
        return pusherVoteAdditionalRate;
    }

    @Override
    public double getDefaultPusherVoteAdditionalRate() {
        return DEFAULT_PUSHER_VOTE_ADDITIONAL_RATE;
    }

    @Override
    public boolean getUseRandomCnIp() {
        return useRandomCnIp;
    }

    @Override
    public boolean getDefaultUseRandomCnIp() {
        return DEFAULT_USE_RANDOM_CN_IP;
    }

    @Override
    public String getCorsAllowOrigin() {
        return corsAllowOrigin;
    }

    @Override
    public String getDefaultCorsAllowOrigin() {
        return DEFAULT_CORS_ALLOW_ORIGIN;
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
    public boolean getDefaultEnableProxy() {
        return DEFAULT_ENABLE_PROXY;
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
    public String getDefaultProxyUrl() {
        return DEFAULT_PROXY_URL;
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
    public boolean getDefaultEnableGeneralUnblock() {
        return DEFAULT_ENABLE_GENERAL_UNBLOCK;
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
    public boolean getDefaultEnableFlac() {
        return DEFAULT_ENABLE_FLAC;
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
    public boolean getDefaultSelectMaxBr() {
        return DEFAULT_SELECT_MAX_BR;
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
    public boolean getDefaultFollowSourceOrder() {
        return DEFAULT_FOLLOW_SOURCE_ORDER;
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
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public void save() {
        config.put(KEY_SERVER_API_BASE_URL, serverApiBaseUrl);
        config.put(KEY_STARTUP_BINARY_API_SERVER, startupBinaryApiServerWhenLaunch);
        config.put(KEY_SERVER_API_BINARY_EXECUTABLE_PATH, serverApiBinaryExecutablePath);
        config.put(KEY_PUSHER_VOTE_ADDITIONAL_RATE, pusherVoteAdditionalRate);
        config.put(KEY_USE_RANDOM_CN_IP, useRandomCnIp);
        config.put(KEY_CORS_ALLOW_ORIGIN, corsAllowOrigin);
        config.put(KEY_ENABLE_PROXY, enableProxy);
        config.put(KEY_PROXY_URL, proxyUrl);
        config.put(KEY_ENABLE_GENERAL_UNBLOCK, enableGeneralUnblock);
        config.put(KEY_ENABLE_FLAC, enableFlac);
        config.put(KEY_SELECT_MAX_BR, selectMaxBr);
        config.put(KEY_FOLLOW_SOURCE_ORDER, followSourceOrder);
        config.put(KEY_PORT, port);
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            yaml.dump(config, writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save MusicHud velocity config", e);
        }
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
