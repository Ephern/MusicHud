package indi.etern.musichud.platform.mod.neoforge.registry;

import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.interfaces.IKeyRegistryService;
import indi.etern.musichud.client.util.HeldKeyGuard;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.LinkedHashMap;

@SuppressWarnings("unused")
public class NeoForgeKeyRegistryService implements IKeyRegistryService {
    private static volatile NeoForgeKeyRegistryService instance;
    private final LinkedHashMap<KeyMapping, Runnable> bindings = new LinkedHashMap<>();
    /** One action per press: the game emits a click for every key repeat while a key is held. */
    private final HeldKeyGuard heldKeyGuard = new HeldKeyGuard();

    private NeoForgeKeyRegistryService() {
        IClientEventService.getInstance().registerClientTickPost(() -> {
            bindings.forEach((mapping, runnable) ->
                    heldKeyGuard.run(mapping, !mapping.isDown(), ignored -> runnable.run()));
        });
    }

    @Override
    public void register(KeyMapping keyMapping, Runnable action) {
        bindings.put(keyMapping, action);
    }

    @SubscribeEvent
    public void registerBindings(RegisterKeyMappingsEvent event) {
        bindings.keySet().forEach(event::register);
    }

    public static NeoForgeKeyRegistryService getInstance() {
        if (instance == null) {
            synchronized (NeoForgeKeyRegistryService.class) {
                if (instance == null) {
                    instance = new NeoForgeKeyRegistryService();
                }
            }
        }
        return instance;
    }
}
