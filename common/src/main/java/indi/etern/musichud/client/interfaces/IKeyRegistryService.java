package indi.etern.musichud.client.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.IKeyRegistryServiceDefinition;
import indi.etern.musichud.platform.Environment;
import net.minecraft.client.KeyMapping;

import java.util.function.Supplier;

public interface IKeyRegistryService extends IKeyRegistryServiceDefinition {
    void register(KeyMapping keyMapping, Runnable action);

    static IKeyRegistryService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IKeyRegistryServiceDefinition> supplier = platform.getKeyRegistryServiceSupplier();
        if (supplier != null) {
            IKeyRegistryServiceDefinition iKeyRegistryServiceDefinition = supplier.get();
            if (iKeyRegistryServiceDefinition instanceof IKeyRegistryService iKeyRegistryService) {
                return iKeyRegistryService;
            } else if (iKeyRegistryServiceDefinition != null) {
                throw new IllegalStateException("KeyRegistryService should implements IKeyRegistryService, not IKeyRegistryServiceDefinition");
            }
        }
        throw new UnsupportedOperationException();
    }
}