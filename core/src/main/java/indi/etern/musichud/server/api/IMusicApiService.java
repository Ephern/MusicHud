package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.server.api.impl.ncm.MusicApiService;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public interface IMusicApiService {
    static IMusicApiService getInstance(ApiProvider apiProvider) {
        if (Objects.requireNonNull(apiProvider) == ApiProvider.NCM) {
            return MusicApiService.getInstance();
        }
        throw new IllegalArgumentException("Invalid api provider");
    }

    Playlist getPlaylistDetail(long id, boolean ignoreCache, @Nullable UUID player);

    List<Album> searchAlbums(String keywords, int offset);

    List<Artist> searchArtists(String keywords, int offset);

    List<MusicDetail> searchMusic(String keywords, int offset);

    List<Playlist> searchPlaylists(String keywords, int offset);

    <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer);

    List<MusicDetail> getMusicDetailByIds(List<Long> ids, UUID playerUUID);

    Album getAlbumInfoDetail(long id, boolean ignoreCache, UUID playerUUID);

    Artist getArtistDetail(long id, UUID playerUUID);

    List<MusicDetail> getArtistMoreMusic(long id, int offset, UUID playerUUID);

    MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, UUID playerUUID);

    UserCategoryPlaylists getPlayersUserPlaylists(boolean ignoreCache, UUID playerUUID);

    LinkedHashSet<Album> getPlayersUserSubscribedAlbums(boolean ignoreCache, UUID playerUUID);

    LinkedHashSet<Artist> getPlayersUserSubscribedArtists(boolean ignoreCache, UUID playerUUID);

    LyricInfo getLyricInfo(MusicDetail musicDetail);

    void addToPlaylist(long playlistId, long musicId, UUID uuid);

    void removeFromPlaylist(long playlistId, long musicId, UUID uuid);

    void userSubscribe(long id, SubscribableType subscribableType, SubscribeAction action, UUID playerUUID);
}