package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.IPayload;
import net.minecraft.world.entity.player.Player;

@FunctionalInterface
public interface NetworkReceiver<T extends IPayload> {
    void receive(T payload, Player player);

    static <T extends IPayload> NetworkReceiver<T> noop() {
        return (payload, player) -> {
        };
    }
}
