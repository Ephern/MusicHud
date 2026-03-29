package indi.etern.musichud.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public interface IServerNetworkService {
    void sendToPlayer(ServerPlayer player, S2CPayload payload);
    void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload);

    static IServerNetworkService getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ModNetworkManager.getInstance();
            }
            case PAPER -> {
                return ReflectionHolder.load("indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager", IServerNetworkService.class);
            }
        }
        throw new UnsupportedOperationException();
    }

    final class ReflectionHolder {
        private ReflectionHolder() {
        }

        static <T> T load(String className, Class<T> expectedType) {
            try {
                Object instance = Class.forName(className).getMethod("getInstance").invoke(null);
                return expectedType.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
