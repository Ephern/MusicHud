package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import net.minecraft.client.KeyMapping;

import java.util.function.Supplier;

public interface IKeyRegistryService {
    void register(KeyMapping keyMapping, Runnable action);

    static IKeyRegistryService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IKeyRegistryService> supplier = platform.getKeyRegistryServiceSupplier();
        if (supplier != null) {
            IKeyRegistryService iKeyRegistryService = supplier.get();
            if (iKeyRegistryService != null) {
                return iKeyRegistryService;
            }
        }
        throw new UnsupportedOperationException();
    }}
