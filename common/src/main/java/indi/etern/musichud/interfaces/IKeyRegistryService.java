package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.architectury.registry.ModKeyRegistryService;
import net.minecraft.client.KeyMapping;

public interface IKeyRegistryService {
    void register(KeyMapping keyMapping, Runnable action);

    static IKeyRegistryService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC,NEOFORGE -> {
                return ModKeyRegistryService.getInstance();
            }
        }
        throw new UnsupportedOperationException();
    }
}
