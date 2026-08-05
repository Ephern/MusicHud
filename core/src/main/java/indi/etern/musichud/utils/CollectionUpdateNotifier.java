package indi.etern.musichud.utils;

import indi.etern.musichud.interfaces.Unregister;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifies subscribers of playlist/album data updates by id.
 * Subscribers are decoupled from bean instance identity: a UI holding a brief
 * (not fully loaded) collection object can still refresh itself by re-resolving
 * the latest cached instance after being notified.
 */
public final class CollectionUpdateNotifier {
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>> PLAYLIST_LISTENERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>> ALBUM_LISTENERS = new ConcurrentHashMap<>();

    private CollectionUpdateNotifier() {
    }

    public static Unregister registerPlaylist(long playlistId, Runnable listener) {
        return register(PLAYLIST_LISTENERS, playlistId, listener);
    }

    public static Unregister registerAlbum(long albumId, Runnable listener) {
        return register(ALBUM_LISTENERS, albumId, listener);
    }

    public static void notifyPlaylistUpdated(long playlistId) {
        notifyListeners(PLAYLIST_LISTENERS, playlistId);
    }

    public static void notifyAlbumUpdated(long albumId) {
        notifyListeners(ALBUM_LISTENERS, albumId);
    }

    private static Unregister register(ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>> listenersMap, long id, Runnable listener) {
        listenersMap.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            var list = listenersMap.get(id);
            if (list != null) list.remove(listener);
        };
    }

    private static void notifyListeners(ConcurrentHashMap<Long, CopyOnWriteArrayList<Runnable>> listenersMap, long id) {
        var list = listenersMap.get(id);
        if (list == null) return;
        for (Runnable listener : list) {
            listener.run();
        }
    }
}
