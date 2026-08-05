package indi.etern.musichud.network;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side subscription table for the channel mechanism.
 * A client is only pushed messages for channels it has subscribed to.
 */
public final class ChannelManager {
    private static final ConcurrentHashMap<String, Set<IPlayerClient>> SUBSCRIBERS = new ConcurrentHashMap<>();

    private ChannelManager() {
    }

    public static void subscribe(IPlayerClient player, Set<String> channels) {
        for (String channel : channels) {
            SUBSCRIBERS.computeIfAbsent(channel, key -> ConcurrentHashMap.newKeySet()).add(player);
        }
    }

    public static void unsubscribe(IPlayerClient player, Set<String> channels) {
        for (String channel : channels) {
            Set<IPlayerClient> set = SUBSCRIBERS.get(channel);
            if (set != null) {
                set.remove(player);
            }
        }
    }

    public static void removePlayer(IPlayerClient player) {
        SUBSCRIBERS.values().forEach(set -> set.remove(player));
    }

    public static Set<IPlayerClient> getSubscribers(String channel) {
        Set<IPlayerClient> set = SUBSCRIBERS.get(channel);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public static boolean isSubscribed(IPlayerClient player, String channel) {
        Set<IPlayerClient> set = SUBSCRIBERS.get(channel);
        return set != null && set.contains(player);
    }
}
