package indi.etern.musichud.server.api.impl.ncm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PlaylistSpecialType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

public class CommonCaches {
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @EqualsAndHashCode
    public static class PlaylistCacheKey {
        long playlistId;
        long userId;

        public static PlaylistCacheKey of(Playlist playlist, long userId) {
            return new PlaylistCacheKey(playlist.getId(), playlist.getSpecialType() == PlaylistSpecialType.USER_SPECIFIC ? userId : -1);
        }

        public static PlaylistCacheKey of(long playlistId) {
            return new PlaylistCacheKey(playlistId, -1);
        }

        public static PlaylistCacheKey of(long playlistId, long userId) {
            return new PlaylistCacheKey(playlistId, userId);
        }
    }
    public static final Cache<PlaylistCacheKey, Playlist> playlistsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    public static final Cache<Long, Album> albumsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    public static final Cache<Long, Artist> artistsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
}
