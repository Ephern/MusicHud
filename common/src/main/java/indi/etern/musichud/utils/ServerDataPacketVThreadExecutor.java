package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.throwable.ApiException;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

public class ServerDataPacketVThreadExecutor {
    public static <T extends IPayload> NetworkReceiver<T> execute(
            BiConsumer<T, Player> consumer
    ) {
        return (payload, player) -> {
            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("MHWorker-Network-V");
                try {
                    consumer.accept(payload, player);
                } catch (ApiException e) {
                    MusicHud.getLogger(payload.getClass()).error(e);
                } catch (Exception e) {
                    MusicHud.getLogger(payload.getClass()).error(e);
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }
            });
        };
    }
}
