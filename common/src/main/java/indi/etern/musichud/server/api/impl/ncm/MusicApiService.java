package indi.etern.musichud.server.api.impl.ncm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.PostProcessable;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.utils.JsonUtil;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MusicApiService implements IMusicApiService {
    private static final Logger logger = MusicHud.getLogger(MusicApiService.class);
    private static final Cache<Long, Playlist> playlistCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, MusicDetail> musicDetailCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(400)
            .build();
    private static final Cache<Long, Artist> artistsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, Album> albumsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, List<Playlist>> userSubscribedPlaylistCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, List<Artist>> userSubscribedArtistsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, List<Album>> userSubscribedAlbumsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static volatile MusicApiService musicApiService;
    private final LoginApiService loginApiService = LoginApiService.getInstance();
    private final Gson gson = JsonUtil.gson;

    public static MusicApiService getInstance() {
        if (MusicApiService.musicApiService == null) {
            synchronized (MusicApiService.class) {
                if (MusicApiService.musicApiService == null) {
                    MusicApiService.musicApiService = new MusicApiService();
                }
            }
        }
        return MusicApiService.musicApiService;
    }

    private List<MusicDetail> appendArtistMusic(int offset, Artist artist, Player player) {
        String rawCookie = loginApiService.getRawCookieOrElse(player, loginApiService::getAnonymousCookie);
        GetArtistMusicResponse response = ApiClient.post(ServerApiMeta.Artist.ALL_SONGS, new ArtistAllMusicRequest(artist.getId(), 50, offset, "time"), rawCookie, true);
        List<Long> musicDetailIds = response.songs.stream().map(MusicDetail::getId).toList();
        artist.setTotalMusicCount(response.total);
        List<MusicDetail> musicDetails = getMusicDetailByIds(musicDetailIds, null);
        artist.getMusicDetails().addAll(musicDetails);
        return musicDetails;
    }

    @Override
    public Playlist getPlaylistDetail(long id, @Nullable Player player) {
        Playlist cached = playlistCache.getIfPresent(id);
        Playlist playlist = Playlist.empty(id);
        if (cached != null && !cached.getTracks().isEmpty()) {
            playlist = cached;
        } else {
            String rawCookie = loginApiService.getRawCookieOrElse(player, loginApiService::getAnonymousCookie);
            PlaylistResponse playlistResponse = ApiClient.post(ServerApiMeta.Playlist.DETAIL, new IdRequest(id), rawCookie, true);
            if (playlistResponse.getCode() == 200) {
                playlist = playlistResponse.getPlaylist();
                playlistCache.put(id, playlist);
            } else {
                logger.error("Failed to get playlist detail of player: {} (response code: {})", Objects.requireNonNull(player).getName().getString(), playlistResponse.getCode());
            }
        }
        LoginApiService.PlayerLoginInfo playerLoginInfo = player == null ? null : loginApiService.playerInfoMap.get(player.getUUID());
        Profile profile = playerLoginInfo != null ? playerLoginInfo.profile : null;
        if (playlist.getPrivacy() == Privacy.PRIVATE && !playlist.getCreator().equals(profile)) {
            return Playlist.privacyBlocked(id, playlist.getCreator());
        } else {
            return playlist;
        }
    }

    @Override
    public List<Album> searchAlbums(String keywords, int offset) {
        SearchAlbumsResult result = search(keywords, offset, 50, SearchType.ALBUM,
                response -> gson.fromJson(response, SearchAlbumsResponseBody.class)
        ).result;
        if (result != null && result.albums != null) {
            List<Album> albums = result.albums;
            if (albums.size() > 50) {
                if (albums.size() > offset) {
                    albums.subList(0, offset).clear();
                    return albums;
                } else {
                    return new ArrayList<>();
                }
            } else {
                return albums;
            }
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public List<Artist> searchArtists(String keywords, int offset) {
        SearchArtistsResult result = search(keywords, offset, 50, SearchType.ARTIST,
                response -> gson.fromJson(response, SearchArtistsResponseBody.class)
        ).result;
        if (result != null && result.artists != null) {
            return result.artists;
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public List<MusicDetail> searchMusic(String keywords, int offset) {
        MusicDetailsResponse result = search(keywords, offset, 50, SearchType.MUSIC,
                response -> gson.fromJson(response, SearchMusicResponseBody.class)
        ).result;
        if (result != null) {
            return result.musicDetails();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public List<Playlist> searchPlaylists(String keywords, int offset) {
        SearchPlaylistsResult result = search(keywords, offset, 50, SearchType.PLAYLIST,
                response -> gson.fromJson(response, SearchPlaylistsResponseBody.class)
        ).result;
        if (result != null && result.playlists != null) {
            return result.playlists;
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer) {
        try {
            var requestBody = new SearchRequestBody(keywords, limit, offset, null, searchType);
            var response = ApiClient.post(ServerApiMeta.Search.CLOUD, requestBody, null, true);
            return transformer.apply(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MusicDetail> getMusicDetailByIds(List<Long> ids, Player player) {
        List<Long> uncachedIds = new ArrayList<>();
        List<MusicDetail> result = new ArrayList<>(ids.size());
        for (long id : ids) {
            MusicDetail cached = musicDetailCache.getIfPresent(id);
            if (cached != null) {
                result.add(cached);
            } else {
                uncachedIds.add(id);
            }
        }
        if (uncachedIds.isEmpty()) {
            return result;
        } else {
            try {
                Object requestBody;
                if (!ids.isEmpty()) {
                    requestBody = new GetDetailsRequestBody(String.join(",", ids.stream().map(String::valueOf).toList()), null);
                } else {
                    return List.of();
                }
                String userCookie = loginApiService.getRawCookieOrElse(player,
                        () -> loginApiService.randomVipCookieOrElse(loginApiService::getAnonymousCookie)
                );
                MusicDetailsResponse response = ApiClient.post(ServerApiMeta.Music.DETAIL, requestBody, userCookie, true);
                List<MusicDetail> musicDetails = response.musicDetails();
                result.addAll(musicDetails);
                for (MusicDetail musicDetail : musicDetails) {
                    musicDetailCache.put(musicDetail.getId(), musicDetail);
                }
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SneakyThrows
    @Override
    public Album getAlbumInfoDetail(long id, Player player) {
        return albumsCache.get(id,
                () -> {
                    String rawCookie = loginApiService.getRawCookieOrElse(player, loginApiService::getAnonymousCookie);
                    GetAlbumDetailResult post = ApiClient.post(ServerApiMeta.Album.DETAIL, new IdRequest(id), rawCookie, true);
                    Album album = post.album();
                    post.songs.forEach(song -> {
                        song.setAlbum(album.shallowCopyBriefInfo());// prevent loop reference
                    });
                    album.setMusicDetails(post.songs);
                    return album;
                }
        );
    }

    @SneakyThrows
    @Override
    public Artist getArtistDetail(long id, Player player) {
        return artistsCache.get(id,
                () -> {
                    String rawCookie = loginApiService.getRawCookieOrElse(player, loginApiService::getAnonymousCookie);
                    Artist artist = ApiClient.post(ServerApiMeta.Artist.DETAIL, new IdRequest(id), rawCookie, true).data.artist;
                    appendArtistMusic(0, artist, player);
                    return artist;
                }
        );
    }

    @SneakyThrows
    @Override
    public List<MusicDetail> getArtistMoreMusic(long id, int offset, Player player) {
        Artist artist = getArtistDetail(id, player);
        return appendArtistMusic(offset, artist, player);
    }

    @Override
    public MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, Player player) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
            return MusicResourceInfo.NONE;
        } else {
            var loginInfo = loginApiService.getLoginInfoByPlayer(player);
            AtomicBoolean vipAccessible = new AtomicBoolean(true);
            String cookie;
            boolean isVip = loginInfo != null && loginInfo.getVipType() == VipType.VIP;
            MusicDetail.ExtraInfo extraInfo = musicDetail.getExtraInfo();
            if (isVip || extraInfo != null && extraInfo.cloudSource()) {
                if (!isVip) {
                    vipAccessible.set(false);
                }
                cookie = loginInfo == null ? loginApiService.getAnonymousCookie() : loginInfo.getLoginCookieInfo().rawCookie();
            } else {
                cookie = loginApiService.randomVipCookieOrElse(() -> {
                    vipAccessible.set(false);
                    return loginInfo == null ? loginApiService.getAnonymousCookie() : loginInfo.getLoginCookieInfo().rawCookie();
                });
            }

            MusicResourceInfo musicResourceInfo;
            int retryCount = 0;
            boolean available;
            do {
                if (retryCount >= 5) {
                    logger.error("Failed to load music resource for \"{}\"(id:{}), as resource url is not available", musicDetail.getName(), musicDetail.getId());
                    return MusicResourceInfo.NONE;
                }
                var request = new GetDirectResourceUrlRequest(musicDetail.getId(), false, quality);
                var response = ApiClient.post(ServerApiMeta.Music.URL, request, cookie, true);
                if (response.code == 200) {
                    musicResourceInfo = response.data.getFirst();
                    // 30 seconds trial or have no copyright
                    if ((extraInfo == null || !extraInfo.cloudSource()) &&
                            ((musicResourceInfo.getFee() == Fee.SEPARATELY_PURCHASE || (musicResourceInfo.getFee() == Fee.VIP && !vipAccessible.get()))
                            ||musicResourceInfo.getUrl() == null)
                    ) {
                        logger.warn("Failed to get resource for music: {} (ID: {}), trying substitute", musicDetail.getName(), musicDetail.getId());
                        musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                    }
                    completeLyricInfo(musicDetail);
                } else {
                    logger.warn("Failed to get resource for music: {} (ID: {}), trying substitute", musicDetail.getName(), musicDetail.getId());
                    try {
                        musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                        completeLyricInfo(musicDetail);
                    } catch (Exception e) {
                        logger.error("Failed to get resource for music from substitute: {} (ID: {})", musicDetail.getName(), musicDetail.getId());
                        musicResourceInfo = MusicResourceInfo.NONE;
                    }
                }
                available = ApiClient.checkUrlAvailable(musicResourceInfo.getUrl(), 10000);
                retryCount++;
            } while (!available);
            return musicResourceInfo;
        }
    }

    private void completeLyricInfo(MusicDetail musicDetail) {
        try {
            LyricInfo lyricInfo = getLyricInfo(musicDetail);
            musicDetail.setLyricInfo(lyricInfo);
        } catch (Exception e) {
            logger.warn("Failed to get lyric for music: {} (ID: {})", musicDetail.getName(), musicDetail.getId(), e);
        }
    }

    private @NotNull MusicResourceInfo getMusicResourceInfoFromMatcher(MusicDetail musicDetail) {
        var unblockRequest = new GetMatchResourceUrlRequest(musicDetail.getId(), null);
        var unblockResponse = ApiClient.post(ServerApiMeta.Music.UNBLOCK, unblockRequest, loginApiService.randomVipCookieOrElse(null), true);
        return MusicResourceInfo.from(unblockResponse.data, musicDetail);
    }

    @Override
    @SneakyThrows
    public List<Playlist> getPlayersUserSubscribedPlaylists(Player player) {
        if (player == null) {
            return Collections.emptyList();
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            long userId = profile.getUserId();
            return userSubscribedPlaylistCache.get(userId, () -> {
                PlaylistsResponse playlistData = ApiClient.post(
                        ServerApiMeta.User.PLAYLIST,
                        new PagedRequestDataWithUID(userId, 50, 0),
                        loginInfo.loginCookieInfo.rawCookie(),
                        true);
                return playlistData.getPlaylists();
            });
        }
    }

    @Override
    @SneakyThrows
    public List<Album> getPlayersUserSubscribedAlbums(Player player) {
        if (player == null) {
            return Collections.emptyList();
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            long userId = profile.getUserId();
            return userSubscribedAlbumsCache.get(userId, () -> {
                UserSubscribedAlbumResponse userSubscribedAlbumResponse = ApiClient.post(
                        ServerApiMeta.User.SUBSCRIBED_ALBUMS,
                        new PagedRequestDataWithUID(userId, 50, 0),
                        loginInfo.loginCookieInfo.rawCookie(),
                        true);
                return userSubscribedAlbumResponse.data();
            });
        }
    }

    @Override
    @SneakyThrows
    public List<Artist> getPlayersUserSubscribedArtists(Player player) {
        if (player == null) {
            return Collections.emptyList();
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            long userId = profile.getUserId();
            return userSubscribedArtistsCache.get(userId, () -> {
                UserSubscribedArtistResponse userSubscribedArtistResponse = ApiClient.post(
                        ServerApiMeta.User.SUBSCRIBED_ARTISTS,
                        new PagedRequestDataWithUID(userId, 50, 0),
                        loginInfo.loginCookieInfo.rawCookie(),
                        true);
                return userSubscribedArtistResponse.data();
            });
        }
    }

    @Override
    public LyricInfo getLyricInfo(MusicDetail musicDetail) {
        var response = ApiClient.post(ServerApiMeta.Music.WORD_BY_WORD_LYRIC, new IdRequest(musicDetail.getId()), loginApiService.randomVipCookieOrElse(loginApiService::getAnonymousCookie), true);
        if (response.getCode() == 200) {
            return response;
        } else {
            throw new RuntimeException("Failed to get lyric for music: " + musicDetail.getName() + " (ID: " + musicDetail.getId() + "), response code:" + response.getCode());
        }
    }

    record IdRequest(long id) {
    }

    record ArtistAllMusicRequest(long id, int limit, int offset, String order/*hot|time*/) {
    }

    record SearchRequestBody(String keywords, int limit, int offset, String cookie, SearchType type) {
    }

    public record GetAlbumDetailResult(Album album, List<MusicDetail> songs) {
    }

    public record SearchAlbumsResult(List<Album> albums) {
    }

    public record SearchAlbumsResponseBody(
            SearchAlbumsResult result
    ) {
    }

    public record SearchArtistsResult(List<Artist> artists) {
    }

    public record GetArtistDetailResponseData(Artist artist) {
    }

    public record GetArtistDetailResponse(GetArtistDetailResponseData data) {
    }

    public record GetArtistMusicResponse(List<MusicDetail> songs, int total) {
    }

    public record SearchArtistsResponseBody(
            SearchArtistsResult result
    ) {
    }

    public record SearchMusicResponseBody(
            MusicDetailsResponse result
    ) {
    }

    public record SearchPlaylistsResult(List<Playlist> playlists) {
    }

    public record SearchPlaylistsResponseBody(
            SearchPlaylistsResult result
    ) {
    }

    record GetDetailsRequestBody(String ids, String cookie) {
    }

    record GetDirectResourceUrlRequest(long id, boolean unblock, Quality level) {
    }

    record GetMatchResourceUrlRequest(long id, String source) {
    }

    public record GetDirectResourceUrlResponse(int code, List<MusicResourceInfo> data) {
    }

    public record GetMatchResourceUrlResponse(int code, String data) {
    }

    public record PagedRequestDataWithUID(long uid, int limit, int offset) {
    }

    public record PlaylistTracksResponse(List<MusicDetail> songs) {
    }

    public record UserSubscribedAlbumResponse(List<Album> data) {
    }

    public record UserSubscribedArtistResponse(List<Artist> data) {
    }

    public static class PlaylistsResponse {
        @Getter
        int code;
        @SerializedName("playlist")
        final
        List<Playlist> playlists = List.of();

        public List<Playlist> getPlaylists() {
            if (playlists.isEmpty()) {
                return List.of();
            }
            return playlists.stream().filter(Objects::nonNull).toList();
        }
    }

    public record MusicDetailsResponse (
            @SerializedName("songs")
            List<MusicDetail> musicDetails,
            @SerializedName("privileges")
            List<MusicDetail.ExtraInfo> extraInfos) implements PostProcessable {

        @Override
        public void postProcess() {
            if (musicDetails != null && extraInfos != null && musicDetails.size() == extraInfos.size()) {
                int size = musicDetails.size();
                for (int i = 0; i < size; i++) {
                    musicDetails.get(i).setExtraInfo(extraInfos.get(i));
                }
            }
        }
    }
}