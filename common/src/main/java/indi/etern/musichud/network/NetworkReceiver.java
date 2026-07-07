package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.IPayload;

@FunctionalInterface
public interface NetworkReceiver<T extends IPayload> {
    void receive(T payload, IPlayerClient player);

    static <T extends IPayload> NetworkReceiver<T> noop() {
        return (payload, player) -> {
        };
    }
}
