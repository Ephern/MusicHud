package indi.etern.musichud.beans.user;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.login.ClientUserDataStore;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProfileConfigData {
    private static volatile ProfileConfigData instance;
    Profile profile;
    Set<IdlePlaySource> idlePlaySources = ConcurrentHashMap.newKeySet();

    public synchronized void save() {
        ClientUserDataStore.saveAccount(this);
    }

    public static ProfileConfigData getInstance() {
        if (instance == null) {
            synchronized (ProfileConfigData.class) {
                if (instance == null) {
                    ProfileConfigData loaded = ClientUserDataStore.loadAccount();
                    if (loaded == null) {
                        // Fallback while the one-time migration has not run yet.
                        try {
                            loaded = ClientConfig.getInstance().getLegacyClientAccountConfig();
                        } catch (RuntimeException ignored) {
                        }
                    }
                    instance = Objects.requireNonNullElseGet(loaded, ProfileConfigData::new);
                    // Gson deserializes the declared Set type into a plain LinkedHashSet;
                    // normalize to a concurrent set because the singleton is mutated from
                    // UI, network and executor threads
                    Set<IdlePlaySource> loadedSources = instance.idlePlaySources;
                    instance.idlePlaySources = ConcurrentHashMap.newKeySet();
                    if (loadedSources != null) {
                        instance.idlePlaySources.addAll(loadedSources);
                    }
                }
            }
        }
        return instance;
    }
}
