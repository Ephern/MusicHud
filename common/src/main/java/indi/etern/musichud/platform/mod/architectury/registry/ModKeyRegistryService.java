package indi.etern.musichud.platform.mod.architectury.registry;

import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import indi.etern.musichud.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.IKeyRegistryService;
import net.minecraft.client.KeyMapping;

import java.util.LinkedHashMap;

@SuppressWarnings("unused")
public class ModKeyRegistryService implements IKeyRegistryService {
    private static volatile ModKeyRegistryService instance;
    private final LinkedHashMap<KeyMapping, Runnable> bindings = new LinkedHashMap<>();

    private ModKeyRegistryService() {
        IClientEventService.getInstance().registerClientTickPost(() -> {
            bindings.forEach((mapping, runnable) -> {
                while (mapping.consumeClick()) {
                    runnable.run();
                }
            });
        });
    }

    @Override
    public void register(KeyMapping keyMapping, Runnable action) {
        bindings.put(keyMapping, action);
        KeyMappingRegistry.register(keyMapping);
    }

    public static ModKeyRegistryService getInstance() {
        if (instance == null) {
            synchronized (ModKeyRegistryService.class) {
                if (instance == null) {
                    instance = new ModKeyRegistryService();
                }
            }
        }
        return instance;
    }
}
