package indi.etern.musichud.beans.user;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProfileConfigData {
    private static volatile ProfileConfigData instance;
    Profile profile;
    Set<IdlePlaySource> idlePlaySources = ConcurrentHashMap.newKeySet();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    @SneakyThrows
    public synchronized void saveToConfig() {
        clientConfig.setClientAccountConfig(this);
        clientConfig.save();
    }

    @SneakyThrows
    public static ProfileConfigData getInstance() {
        if (instance == null) {
            synchronized (ProfileConfigData.class) {
                if (instance == null) {
                    ProfileConfigData clientAccountConfig = clientConfig.getClientAccountConfig();
                    instance = Objects.requireNonNullElseGet(clientAccountConfig, ProfileConfigData::new);
                    // Gson deserializes the declared Set type into a plain LinkedHashSet;
                    // normalize to a concurrent set because the singleton is mutated from
                    // UI, network and executor threads
                    Set<IdlePlaySource> loaded = instance.idlePlaySources;
                    instance.idlePlaySources = ConcurrentHashMap.newKeySet();
                    if (loaded != null) {
                        instance.idlePlaySources.addAll(loaded);
                    }
                }
            }
        }
        return instance;
    }
}