package indi.etern.musichud.utils;

import indi.etern.musichud.interfaces.Unregister;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Notifies subscribers of playlist/album data updates by id.
 * Subscribers are decoupled from bean instance identity: a UI holding a brief
 * (not fully loaded) collection object can still refresh itself by re-resolving
 * the latest cached instance after being notified.
 * <p>
 * The {@code self} flag marks updates caused by the local client's own
 * optimistic operations (already reflected locally before the request
 * completes); subscribers may skip re-syncing those to avoid redundant
 * add/remove churn during rapid toggling.
 */
public final class CollectionUpdateNotifier {
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<Boolean>>> PLAYLIST_LISTENERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<Boolean>>> ALBUM_LISTENERS = new ConcurrentHashMap<>();

    private CollectionUpdateNotifier() {
    }

    public static Unregister registerPlaylist(long playlistId, Consumer<Boolean> listener) {
        return register(PLAYLIST_LISTENERS, playlistId, listener);
    }

    public static Unregister registerAlbum(long albumId, Consumer<Boolean> listener) {
        return register(ALBUM_LISTENERS, albumId, listener);
    }

    public static void notifyPlaylistUpdated(UUID operator, long playlistId) {
        notifyPlaylistUpdated(playlistId, IClientDistUtil.getInstance().getClientPlayerUUID().equals(operator));
    }

    public static void notifyPlaylistUpdated(long playlistId, boolean self) {
        notifyListeners(PLAYLIST_LISTENERS, playlistId, self);
    }

    public static void notifyAlbumUpdated(UUID operator, long albumId) {
        notifyAlbumUpdated(albumId, IClientDistUtil.getInstance().getClientPlayerUUID().equals(operator));
    }

    public static void notifyAlbumUpdated(long albumId, boolean self) {
        notifyListeners(ALBUM_LISTENERS, albumId, self);
    }

    private static Unregister register(ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<Boolean>>> listenersMap, long id, Consumer<Boolean> listener) {
        listenersMap.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            var list = listenersMap.get(id);
            if (list != null) list.remove(listener);
        };
    }

    private static void notifyListeners(ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<Boolean>>> listenersMap, long id, boolean self) {
        var list = listenersMap.get(id);
        if (list == null) return;
        for (Consumer<Boolean> listener : list) {
            listener.accept(self);
        }
    }
}
