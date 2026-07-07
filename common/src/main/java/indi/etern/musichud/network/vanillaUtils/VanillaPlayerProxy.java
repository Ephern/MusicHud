package indi.etern.musichud.network.vanillaUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.ClientDistUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VanillaPlayerProxy implements IPlayerClient {
    @Getter
    private final Player player;

    private static final Cache<Player, VanillaPlayerProxy> playerProxyCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();
    private final ClientType clientType;

    private VanillaPlayerProxy(Player player) {
        this.player = player;
        Environment.Side currentSide = MusicHud.getCurrentEnvironment().getSide();
        clientType = currentSide == Environment.Side.CLIENT && ClientDistUtil.isLocalPlayer(player) ? ClientType.LOCAL : ClientType.REMOTE;
    }

    @SneakyThrows
    public static VanillaPlayerProxy ofPlayer(Player player) {
        return playerProxyCache.get(player, () -> new VanillaPlayerProxy(player));
    }

    @Override
    public UUID getUUID() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public ClientType getClientType() {
        return clientType;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VanillaPlayerProxy vanillaPlayerProxy && player.getUUID().equals(vanillaPlayerProxy.getUUID());
    }

    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}
