package indi.etern.musichud.platform.mod.fabric.registry;

import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.interfaces.IKeyRegistryService;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import java.util.LinkedHashMap;

@SuppressWarnings("unused")
public class FabricKeyRegistryService implements IKeyRegistryService {
    private static volatile FabricKeyRegistryService instance;
    private final LinkedHashMap<KeyMapping, Runnable> bindings = new LinkedHashMap<>();

    private FabricKeyRegistryService() {
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
        KeyBindingHelper.registerKeyBinding(keyMapping);
    }

    public static FabricKeyRegistryService getInstance() {
        if (instance == null) {
            synchronized (FabricKeyRegistryService.class) {
                if (instance == null) {
                    instance = new FabricKeyRegistryService();
                }
            }
        }
        return instance;
    }
}
