package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.NetworkReceiver;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public class ServerDataPacketVThreadExecutor {
    public static <T extends IPayload> NetworkReceiver<T> execute(
            BiConsumer<T, ServerPlayer> consumer
    ) {
        return (payload, player) -> {
            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("MHWorker-Network-V");
                if (player instanceof ServerPlayer serverPlayer) {
                    try {
                        consumer.accept(payload, serverPlayer);
                    } catch (Exception e) {
                        MusicHud.getLogger(payload.getClass()).error(e);
                        //noinspection CallToPrintStackTrace
                        e.printStackTrace();
                    }
                } else {
                    throw new IllegalStateException("Player must be a server player");
                }
            });
        };
    }
}
