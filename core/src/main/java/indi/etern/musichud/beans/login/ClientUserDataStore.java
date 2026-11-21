package indi.etern.musichud.beans.login;

import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.ClientDataFileStore;
import indi.etern.musichud.utils.JsonUtil;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Combined client user data (login cookie and account profile). It is stored in one file that
 * follows the config option between the instance directory and the user home directory.
 */
public final class ClientUserDataStore {
    private static final Logger logger = MusicHud.getLogger(ClientUserDataStore.class);
    private static final Path LOCAL_PATH = Paths.get("music-hud", ".credentials");
    private static final Path GLOBAL_PATH = Paths.get(
            System.getProperty("user.home", "."), ".music-hud", ".credentials");
    private static final Path LOCAL_ACCOUNT_PATH = Paths.get("music-hud", ".account");
    private static final Path GLOBAL_ACCOUNT_PATH = Paths.get(
            System.getProperty("user.home", "."), ".music-hud", ".account");
    private static final Object LOCK = new Object();

    private static volatile ClientConfig clientConfig;
    private static volatile ClientUserData cached;
    private static volatile boolean cacheLoaded;
    private static volatile boolean cacheGlobal;
    private static boolean initialized;

    private ClientUserDataStore() {
    }

    /**
     * Runs once per process after the client config was loaded. Migrates legacy config entries and
     * separate cookie/account files into the combined file and adopts an existing global file on
     * first launch.
     */
    public static void initialize() {
        if (!isClientSide()) {
            return;
        }
        try {
            synchronized (LOCK) {
                if (initialized) {
                    return;
                }
                ClientConfig config = config();
                boolean firstLaunch = !config.isClientDataStorageInitialized();
                boolean changed = firstLaunch;

                if (firstLaunch && !config.getUseGlobalCredentials()) {
                    ClientUserData globalData = readData(GLOBAL_PATH);
                    if (globalData != null && isRealCookie(globalData.cookie())) {
                        config.setUseGlobalCredentials(true);
                        logger.info("Adopted global client user data from {}", GLOBAL_PATH.toAbsolutePath());
                    }
                }

                boolean global = config.getUseGlobalCredentials();
                Path active = activePath(global);
                ClientUserData activeData = readData(active);
                ClientUserData otherData = readData(activePath(!global));
                LoginCookieInfo legacyCookie = config.getLegacyClientCookie();
                ProfileConfigData legacyAccount = config.getLegacyClientAccountConfig();

                LoginCookieInfo cookie = activeData != null ? activeData.cookie() : null;
                if (cookie == null && otherData != null) {
                    cookie = otherData.cookie();
                }
                if (cookie == null) {
                    cookie = legacyCookie;
                }

                ProfileConfigData account = activeData != null ? activeData.account() : null;
                if (account == null && otherData != null) {
                    account = otherData.account();
                }
                if (account == null) {
                    account = ClientDataFileStore.read(accountPath(global), ProfileConfigData.class);
                }
                if (account == null) {
                    account = ClientDataFileStore.read(accountPath(!global), ProfileConfigData.class);
                }
                if (account == null) {
                    account = legacyAccount;
                }

                ClientUserData merged = new ClientUserData(cookie, account);
                boolean wrote = false;
                if (!merged.isEmpty() && (firstLaunch || !merged.equals(activeData))) {
                    wrote = ClientDataFileStore.write(active, merged);
                    if (wrote) {
                        changed = true;
                    } else {
                        logger.warn("Failed to write client user data to {}", active.toAbsolutePath());
                    }
                }

                boolean cookieStored = merged.cookie() != null
                        && (wrote || (activeData != null && Objects.equals(activeData.cookie(), merged.cookie())));
                boolean accountStored = merged.account() != null
                        && (wrote || (activeData != null && merged.account().equals(activeData.account())));
                if (legacyCookie != null && cookieStored) {
                    config.clearLegacyClientCookie();
                    changed = true;
                }
                if (legacyAccount != null && accountStored) {
                    config.clearLegacyClientAccountConfig();
                    changed = true;
                }
                if (accountStored) {
                    deleteLegacyAccountFiles();
                }
                if (firstLaunch) {
                    config.setClientDataStorageInitialized(true);
                }
                if (changed) {
                    config.save();
                }
                invalidateLocked();
                initialized = true;
            }
        } catch (RuntimeException e) {
            logger.error("Failed to initialize client user data storage", e);
        }
    }

    public static LoginCookieInfo currentCookie() {
        LoginCookieInfo cookie = data().cookie();
        return cookie != null ? cookie : LoginCookieInfo.UNLOGGED;
    }

    public static void saveCookie(LoginCookieInfo cookie) {
        if (cookie == null) {
            return;
        }
        synchronized (LOCK) {
            ClientConfig config = config();
            boolean global = config.getUseGlobalCredentials();
            ClientUserData current = loadedData(config, global);
            ClientUserData updated = new ClientUserData(cookie, current.account());
            if (!ClientDataFileStore.write(activePath(global), updated)) {
                logger.error("Failed to save client cookie to {}", activePath(global).toAbsolutePath());
                return;
            }
            cached = updated;
            cacheGlobal = global;
            cacheLoaded = true;
        }
    }

    public static ProfileConfigData loadAccount() {
        return data().account();
    }

    public static void saveAccount(ProfileConfigData account) {
        if (account == null) {
            return;
        }
        synchronized (LOCK) {
            ClientConfig config = config();
            boolean global = config.getUseGlobalCredentials();
            ClientUserData current = loadedData(config, global);
            ClientUserData updated = new ClientUserData(current.cookie(), account);
            if (!ClientDataFileStore.write(activePath(global), updated)) {
                logger.warn("Failed to save client account data to {}", activePath(global).toAbsolutePath());
                return;
            }
            cached = updated;
            cacheGlobal = global;
            cacheLoaded = true;
        }
    }

    /**
     * @return {@code false} when the requested switch could not preserve the current data
     */
    public static boolean setGlobalStorageEnabled(boolean enabled, boolean deleteGlobalOnDisable) {
        synchronized (LOCK) {
            ClientConfig config = config();
            boolean wasGlobal = config.getUseGlobalCredentials();
            if (enabled == wasGlobal) {
                if (!enabled && deleteGlobalOnDisable) {
                    deleteGlobalCredentials();
                }
                return true;
            }
            try {
                if (enabled) {
                    ClientUserData localData = loadedData(config, false);
                    ClientUserData globalData = loadedData(config, true);
                    LoginCookieInfo cookie;
                    if (globalData.cookie() != null && isRealCookie(globalData.cookie())) {
                        cookie = globalData.cookie();
                    } else if (localData.cookie() != null) {
                        cookie = localData.cookie();
                    } else {
                        cookie = globalData.cookie();
                    }
                    ProfileConfigData account = globalData.account() != null
                            ? globalData.account()
                            : localData.account();
                    ClientUserData merged = new ClientUserData(cookie, account);
                    if (!merged.isEmpty() && !ClientDataFileStore.write(GLOBAL_PATH, merged)) {
                        logger.error("Failed to write global client user data to {}", GLOBAL_PATH.toAbsolutePath());
                        return false;
                    }
                    if (merged.account() != null) {
                        deleteLegacyAccountFiles();
                    }
                } else {
                    ClientUserData globalData = loadedData(config, true);
                    if (!globalData.isEmpty() && !ClientDataFileStore.write(LOCAL_PATH, globalData)) {
                        logger.error("Failed to write local client user data to {}", LOCAL_PATH.toAbsolutePath());
                        return false;
                    }
                    if (globalData.account() != null) {
                        deleteLegacyAccountFiles();
                    }
                }
                config.setUseGlobalCredentials(enabled);
                try {
                    config.save();
                } catch (RuntimeException e) {
                    config.setUseGlobalCredentials(wasGlobal);
                    throw e;
                }
                if (!enabled && deleteGlobalOnDisable) {
                    deleteGlobalCredentials();
                }
                invalidateLocked();
                logger.info("Switched client user data storage to {}", activeDirectory().toAbsolutePath());
                return true;
            } catch (RuntimeException e) {
                logger.error("Failed to switch client user data storage", e);
                return false;
            }
        }
    }

    public static boolean deleteGlobalCredentials() {
        boolean deleted = ClientDataFileStore.delete(GLOBAL_PATH);
        deleted |= ClientDataFileStore.delete(GLOBAL_ACCOUNT_PATH);
        if (deleted) {
            logger.info("Deleted global client user data in {}", GLOBAL_PATH.getParent().toAbsolutePath());
        }
        return deleted;
    }

    public static boolean globalCredentialsExist() {
        return ClientDataFileStore.exists(GLOBAL_PATH) || ClientDataFileStore.exists(GLOBAL_ACCOUNT_PATH);
    }

    public static Path activePath() {
        return activePath(isGlobalStorageEnabled());
    }

    public static Path activeDirectory() {
        return activePath().toAbsolutePath().getParent();
    }

    public static Path globalPath() {
        return GLOBAL_PATH;
    }

    public static boolean isGlobalStorageEnabled() {
        return config().getUseGlobalCredentials();
    }

    private static ClientUserData data() {
        ClientConfig config = config();
        boolean global = config.getUseGlobalCredentials();
        if (cacheLoaded && cacheGlobal == global) {
            return cached;
        }
        synchronized (LOCK) {
            global = config.getUseGlobalCredentials();
            if (cacheLoaded && cacheGlobal == global) {
                return cached;
            }
            ClientUserData loaded = loadedData(config, global);
            cached = loaded;
            cacheGlobal = global;
            cacheLoaded = true;
            return loaded;
        }
    }

    private static ClientUserData loadedData(ClientConfig config, boolean global) {
        ClientUserData data = readData(activePath(global));
        if (data == null) {
            data = new ClientUserData(null, null);
        }
        if (data.cookie() == null) {
            data = new ClientUserData(config.getLegacyClientCookie(), data.account());
        }
        if (data.account() == null) {
            ProfileConfigData account = ClientDataFileStore.read(accountPath(global), ProfileConfigData.class);
            if (account == null) {
                account = ClientDataFileStore.read(accountPath(!global), ProfileConfigData.class);
            }
            if (account == null) {
                account = config.getLegacyClientAccountConfig();
            }
            if (account != null) {
                data = new ClientUserData(data.cookie(), account);
            }
        }
        return data;
    }

    private static ClientUserData readData(Path path) {
        JsonObject json = ClientDataFileStore.readJsonObject(path);
        if (json == null) {
            return null;
        }
        try {
            if (json.has("cookie") || json.has("account")) {
                return JsonUtil.gson.fromJson(json, ClientUserData.class);
            }
            if (json.has("rawCookie") || json.has("type")) {
                return new ClientUserData(JsonUtil.gson.fromJson(json, LoginCookieInfo.class), null);
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to parse client user data at {}: {}",
                    path.toAbsolutePath(), e.getClass().getSimpleName());
        }
        return null;
    }

    private static boolean isRealCookie(LoginCookieInfo info) {
        return info != null
                && info.type() != null
                && info.generateTime() != null
                && info.type() != LoginType.UNLOGGED
                && info.type() != LoginType.ANONYMOUS
                && info.rawCookie() != null
                && !info.rawCookie().isBlank();
    }

    private static Path activePath(boolean global) {
        return global ? GLOBAL_PATH : LOCAL_PATH;
    }

    private static Path accountPath(boolean global) {
        return global ? GLOBAL_ACCOUNT_PATH : LOCAL_ACCOUNT_PATH;
    }

    private static void deleteLegacyAccountFiles() {
        ClientDataFileStore.delete(LOCAL_ACCOUNT_PATH);
        ClientDataFileStore.delete(GLOBAL_ACCOUNT_PATH);
    }

    private static void invalidateLocked() {
        cacheLoaded = false;
        cached = null;
    }

    private static ClientConfig config() {
        ClientConfig config = clientConfig;
        if (config == null) {
            synchronized (LOCK) {
                config = clientConfig;
                if (config == null) {
                    config = ClientConfig.getInstance();
                    clientConfig = config;
                }
            }
        }
        return config;
    }

    private static boolean isClientSide() {
        Environment environment = MusicHud.getCurrentEnvironment();
        return environment != null && environment.getSide() == Environment.Side.CLIENT;
    }
}
