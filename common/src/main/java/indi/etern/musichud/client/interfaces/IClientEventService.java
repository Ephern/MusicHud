package indi.etern.musichud.client.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.IClientEventServiceDefinition;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.platform.Environment;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IClientEventService extends IClientEventServiceDefinition {
    static IClientEventService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<IClientEventServiceDefinition> supplier = platform.getClientEventServiceSupplier();
        if (supplier != null) {
            IClientEventServiceDefinition iClientEventServiceDefinition = supplier.get();
            if (iClientEventServiceDefinition instanceof IClientEventService iClientEventService) {
                return iClientEventService;
            } else if (iClientEventServiceDefinition != null) {
                throw new IllegalStateException("ClientEventService should implements IClientEventService, not IClientEventServiceDefinition");
            }
        }
        throw new UnsupportedOperationException();
    }

    Unregister registerClientPlayerJoin(Consumer<Player> listener);
    Unregister registerClientPlayerQuit(Consumer<Player> listener);
    Unregister registerClientTickPost(Runnable listener);
}