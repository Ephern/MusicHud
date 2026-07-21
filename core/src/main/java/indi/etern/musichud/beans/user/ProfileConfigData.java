package indi.etern.musichud.beans.user;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProfileConfigData {
    private static volatile ProfileConfigData instance;
    Profile profile;
    Set<IdlePlaySource> idlePlaySources = new HashSet<>();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    @SneakyThrows
    public void saveToConfig() {
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
                }
            }
        }
        return instance;
    }
}