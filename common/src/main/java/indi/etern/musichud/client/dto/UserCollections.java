package indi.etern.musichud.client.dto;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.impl.ncm.CommonCaches;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.*;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserCollections implements IClientMusicService.IUserCollections {
    private UserCategoryPlaylists userCategoryPlaylists;
    @Setter
    private ObservableSequencedSet<Album> subscribedAlbums;
    @Setter
    private ObservableSequencedSet<Artist> subscribedArtists;
    @Getter
    @Setter
    private boolean loaded = false;
    private volatile long lastReupdateCachesTimestamp = 0;

    private final List<Unregister> playlistUnregisters = new ArrayList<>();

    public void setUserCategoryPlaylists(UserCategoryPlaylists userCategoryPlaylists) {
        this.userCategoryPlaylists = userCategoryPlaylists;
        playlistUnregisters.forEach(Unregister::unregister);
        playlistUnregisters.clear();

        Playlist playlist1 = userCategoryPlaylists.getLikeList();
        registerPlaylistUpdater(playlist1);

        userCategoryPlaylists.getCreatedPlaylist().forEach(this::registerPlaylistUpdater);
        userCategoryPlaylists.getSubscribedPlaylist().forEach(this::registerPlaylistUpdater);
    }

    private void registerPlaylistUpdater(Playlist playlist1) {
        long likeListId = playlist1.getId();
        Unregister unregister1 = CollectionUpdateNotifier.registerPlaylist(likeListId, (self) -> {
            MusicService.getInstance().loadPlaylistDetail(likeListId, false).thenAccept((playlist) -> {
                playlist1.updateFrom(playlist, false);
            });
        });
        playlistUnregisters.add(unregister1);
    }

    public UserCategoryPlaylists getUserCategoryPlaylists() {
        reupdateCachesAsync();
        return userCategoryPlaylists;
    }

    public ObservableSequencedSet<Album> getSubscribedAlbums() {
        reupdateCachesAsync();
        return subscribedAlbums;
    }

    public ObservableSequencedSet<Artist> getSubscribedArtists() {
        reupdateCachesAsync();
        return subscribedArtists;
    }

    private void reupdateCachesAsync() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastReupdateCachesTimestamp >= 60000) {
            lastReupdateCachesTimestamp = currentTimeMillis;
            MusicHud.EXECUTOR.submit(() -> {
                if (userCategoryPlaylists != null) {
                    Playlist likeList = userCategoryPlaylists.getLikeList();
                    if (playlistsCache.asMap().putIfAbsent(CommonCaches.PlaylistCacheKey.of(likeList, Profile.getCurrent().getUserId()), likeList) == null) {
                        CollectionUpdateNotifier.notifyPlaylistUpdated(likeList.getId(), true);
                    }
                    userCategoryPlaylists.getCreatedPlaylist()
                            .forEach(playlist -> {
                                if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                        && playlistsCache.asMap().putIfAbsent(CommonCaches.PlaylistCacheKey.of(playlist, Profile.getCurrent().getUserId()), playlist) == null) {
                                    CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId(), true);
                                }
                            });
                    userCategoryPlaylists.getSubscribedPlaylist()
                            .forEach(playlist -> {
                                if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                        && playlistsCache.asMap().putIfAbsent(CommonCaches.PlaylistCacheKey.of(playlist, Profile.getCurrent().getUserId()), playlist) == null) {
                                    CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId(), true);
                                }
                            });
                }
                if (subscribedAlbums != null) {
                    subscribedAlbums.forEach(album -> {
                        if (album.getMusicDetails() != null && album.getMusicDetails().size() == album.getMusicTrackCount()
                                && albumsCache.asMap().putIfAbsent(album.getId(), album) == null) {
                            CollectionUpdateNotifier.notifyAlbumUpdated(album.getId(), true);
                        }
                    });
                }
                if (subscribedArtists != null) {
                    subscribedArtists.forEach(artist -> {
                        if (artist.getMusicDetails() != null && !artist.getMusicDetails().isEmpty() && !artist.getDescription().isEmpty()) {
                            artistsCache.asMap().putIfAbsent(artist.getId(), artist);
                        }
                    });
                }
            });
        }
    }

    public void syncUserCategoryPlaylists(UserCategoryPlaylists fresh) {
        if (fresh == null) {
            return;
        }
        UserCategoryPlaylists old = userCategoryPlaylists;
        if (old == null) {
            userCategoryPlaylists = fresh;
            return;
        }
        Playlist oldLike = old.getLikeList();
        Playlist freshLike = fresh.getLikeList();
        if (oldLike != null && freshLike != null) {
            if (oldLike.updateFromBrief(freshLike)) {
                CollectionUpdateNotifier.notifyPlaylistUpdated(oldLike.getId(), false);
            }
        }
        syncPlaylistSet(old.getCreatedPlaylist(), fresh.getCreatedPlaylist());
        syncPlaylistSet(old.getSubscribedPlaylist(), fresh.getSubscribedPlaylist());
    }

    private void syncPlaylistSet(ObservableSequencedSet<Playlist> oldSet, ObservableSequencedSet<Playlist> freshSet) {
        if (freshSet == null) {
            return;
        }
        for (Playlist fresh : freshSet) {
            oldSet.stream().filter(playlist -> playlist.equalsLoose(fresh)).findFirst().ifPresent(playlist -> {
                if (playlist.updateFromBrief(fresh)) {
                    CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId(), false);
                }
            });
        }
        oldSet.syncWith(new ObservableSequencedSet<>(freshSet), true);
    }

    public void syncSubscribedAlbums(LinkedHashSet<Album> freshSet) {
        if (freshSet == null) {
            return;
        }
        if (subscribedAlbums == null) {
            subscribedAlbums = new ObservableSequencedSet<>(freshSet);
            return;
        }
        for (Album fresh : freshSet) {
            subscribedAlbums.stream().filter(album -> album.equalsLoose(fresh)).findFirst().ifPresent(album -> {
                if (album.updateFromBrief(fresh)) {
                    CollectionUpdateNotifier.notifyAlbumUpdated(album.getId(), false);
                }
            });
        }
        subscribedAlbums.syncWith(new ObservableSequencedSet<>(freshSet), true);
    }

    public void syncSubscribedArtists(LinkedHashSet<Artist> freshSet) {
        if (freshSet == null) {
            return;
        }
        if (subscribedArtists == null) {
            subscribedArtists = new ObservableSequencedSet<>(freshSet);
            return;
        }
        subscribedArtists.syncWith(new ObservableSequencedSet<>(freshSet), true);
    }
}
