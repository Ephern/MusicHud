package indi.etern.musichud.platform.plugin.velocity.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.proxy.Player;
import indi.etern.musichud.network.IPlayerClient;
import lombok.SneakyThrows;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VelocityPlayerProxy implements IPlayerClient {
    private static final Cache<Player, VelocityPlayerProxy> playerProxyCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    private final Player player;

    private VelocityPlayerProxy(Player player) {
        this.player = player;
    }

    @SneakyThrows
    public static VelocityPlayerProxy ofPlayer(Player player) {
        return playerProxyCache.get(player, () -> new VelocityPlayerProxy(player));
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public ClientType getClientType() {
        return ClientType.REMOTE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VelocityPlayerProxy playerProxy && getUUID().equals(playerProxy.getUUID());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getUUID());
    }
}
