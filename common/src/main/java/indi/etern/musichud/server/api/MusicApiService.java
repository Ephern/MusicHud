package indi.etern.musichud.server.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.MusicDetailsResponse;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.utils.JsonUtil;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MusicApiService {
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
    private static final Cache<Long, AlbumInfo> albumsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static volatile MusicApiService musicApiService;
    private final LoginApiService loginApiService = LoginApiService.getInstance();
    private final Gson gson = JsonUtil.gson;

    public static MusicApiService getInstance() {
        if (musicApiService == null) {
            synchronized (MusicApiService.class) {
                if (musicApiService == null) {
                    musicApiService = new MusicApiService();
                }
            }
        }
        return musicApiService;
    }

    private List<MusicDetail> appendArtistMusic(int offset, Artist artist, ServerPlayer serverPlayer) {
        String rawCookie = getRawCookie(serverPlayer);
        GetArtistMusicResponse response = ApiClient.post(ServerApiMeta.Artist.ALL_SONGS, new ArtistAllMusicRequest(artist.getId(), 50, offset, "time"), rawCookie);
        List<Long> musicDetailIds = response.songs.stream().map(MusicDetail::getId).toList();
        artist.setTotalMusicCount(response.total);
        List<MusicDetail> musicDetails = getMusicDetailByIds(musicDetailIds);
        artist.getMusicDetails().addAll(musicDetails);
        return musicDetails;
    }

    private static String getRawCookie(ServerPlayer serverPlayer) {
        String rawCookie;
        LoginApiService.PlayerLoginInfo loginInfo = LoginApiService.getInstance().loginedPlayerInfoMap.get(serverPlayer);
        if (loginInfo != null) {
            rawCookie = loginInfo.loginCookieInfo.rawCookie();
        } else {
            rawCookie = LoginApiService.getInstance().getAnonymousCookie();
        }
        return rawCookie;
    }

    public Playlist getPlaylistDetail(long id, @Nullable ServerPlayer serverPlayer) {
        Playlist cached = playlistCache.getIfPresent(id);
        if (cached != null) {
            return cached;
        } else {
            String rawCookie = getRawCookie(serverPlayer);
            PlaylistResponse playlistResponse = ApiClient.post(ServerApiMeta.Playlist.DETAIL, new IdRequest(id), rawCookie);
            if (playlistResponse.getCode() == 200) {
                Playlist playlist = playlistResponse.getPlaylist();
                playlistCache.put(id, playlist);
                return playlist;
            } else {
                logger.error("Failed to get playlist detail of player: {} (response code: {})", Objects.requireNonNull(serverPlayer).getName().getString(), playlistResponse.getCode());
                return null;
            }
        }
    }

    public List<AlbumInfo> searchAlbums(String keywords, int offset) {
        SearchAlbumsResult result = search(keywords, offset, SearchType.ALBUM,
                response -> gson.fromJson(response, SearchAlbumsResponseBody.class)
        ).result;
        if (result != null && result.albums != null) {
            return result.albums;
        } else {
            return new ArrayList<>();
        }
    }

    public List<Artist> searchArtists(String keywords, int offset) {
        SearchArtistsResult result = search(keywords, offset, SearchType.ARTIST,
                response -> gson.fromJson(response, SearchArtistsResponseBody.class)
        ).result;
        if (result != null && result.artists != null) {
            return result.artists;
        } else {
            return new ArrayList<>();
        }
    }

    public List<MusicDetail> searchMusic(String keywords, int offset) {
        MusicDetailsResponse result = search(keywords, offset, SearchType.MUSIC,
                response -> gson.fromJson(response, SearchMusicResponseBody.class)
        ).result;
        if (result != null) {
            return result.getMusicDetails();
        } else {
            return new ArrayList<>();
        }
    }

    public List<Playlist> searchPlaylists(String keywords, int offset) {
        SearchPlaylistsResult result = search(keywords, offset, SearchType.PLAYLIST,
                response -> gson.fromJson(response, SearchPlaylistsResponseBody.class)
        ).result;
        if (result != null && result.playlists != null) {
            return result.playlists;
        } else {
            return new ArrayList<>();
        }
    }

    public <T> T search(String keywords, int offset, SearchType searchType, Function<String, T> transformer) {
        try {
            var requestBody = new SearchRequestBody(keywords, 50, offset, null, searchType);
            var response = ApiClient.post(ServerApiMeta.Search.CLOUD, requestBody, null);
            return transformer.apply(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<MusicDetail> getMusicDetailByIds(List<Long> ids) {
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
                    requestBody = new GetDetailsRequestBody(String.join(",",ids.stream().map(String::valueOf).toList()), null);
                } else {
                    return List.of();
                }
                String userCookie = loginApiService.randomVipCookieOr(null);
                var response = ApiClient.post(ServerApiMeta.Music.DETAIL, requestBody, userCookie);
                List<MusicDetail> musicDetails = response.getMusicDetails();
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
    public AlbumInfo getAlbumInfoDetail(long id, ServerPlayer serverPlayer) {
        return albumsCache.get(id,
                () -> {
                    String rawCookie = getRawCookie(serverPlayer);
                    GetAlbumDetailResult post = ApiClient.post(ServerApiMeta.Album.DETAIL, new IdRequest(id), rawCookie);
                    AlbumInfo album = post.album();
                    post.songs.forEach(song -> {
                        song.setAlbum(album.shallowCopyBriefInfo());// prevent loop reference
                    });
                    album.setMusicDetails(post.songs);
                    return album;
                }
        );
    }

    @SneakyThrows
    public Artist getArtistDetail(long id, ServerPlayer serverPlayer) {
        return artistsCache.get(id,
                () -> {
                    String rawCookie = getRawCookie(serverPlayer);
                    Artist artist = ApiClient.post(ServerApiMeta.Artist.DETAIL, new IdRequest(id), rawCookie).data.artist;
                    appendArtistMusic(0, artist, serverPlayer);
                    return artist;
                }
        );
    }

    @SneakyThrows
    public List<MusicDetail> getArtistMoreMusic(long id, int offset, ServerPlayer serverPlayer) {
        Artist artist = getArtistDetail(id, serverPlayer);
        return appendArtistMusic(offset, artist, serverPlayer);
    }

    public MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, String cookie) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
            return MusicResourceInfo.NONE;
        } else {
            MusicResourceInfo musicResourceInfo;
            int retryCount = 0;
            boolean available;
            do {
                if (retryCount >= 5) {
                    logger.error("Failed to load music resource for \"{}\"(id:{}), as resource url is not available", musicDetail.getName(), musicDetail.getId());
                    return MusicResourceInfo.NONE;
                }
                var request = new GetDirectResourceUrlRequest(musicDetail.getId(), false, quality);
                var response = ApiClient.post(ServerApiMeta.Music.URL, request, cookie);
                if (response.code == 200) {
                    musicResourceInfo = response.data.getFirst();
                    // 30 seconds trial or have no copyright
                    if (musicResourceInfo.getTime() <= 30040 || musicResourceInfo.getUrl() == null) {
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
        var unblockResponse = ApiClient.post(ServerApiMeta.Music.UNBLOCK, unblockRequest, loginApiService.randomVipCookieOr(null));
        return MusicResourceInfo.from(unblockResponse.data, musicDetail);
    }

    public List<Playlist> getPlayersUserSubsctibedPlaylists(ServerPlayer player) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByServerPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            PlaylistsResponse playlistData = ApiClient.post(
                    ServerApiMeta.User.PLAYLIST,
                    new PagedRequestDataWithUID(profile.getUserId(), 50, 0),
                    loginInfo.loginCookieInfo.rawCookie()
            );
            return playlistData.getPlaylists();
        }
    }

    public List<Playlist> getPlayersUserSubscribedAlbums(ServerPlayer player) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByServerPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            PlaylistsResponse playlistData = ApiClient.post(
                    ServerApiMeta.User.PLAYLIST,
                    new PagedRequestDataWithUID(profile.getUserId(), 50, 0),
                    loginInfo.loginCookieInfo.rawCookie()
            );
            return playlistData.getPlaylists();
        }
    }

    public List<Playlist> getPlayersUserSubscribedArtists(ServerPlayer player) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByServerPlayer(player);
        Profile profile = loginInfo.profile;
        if (profile == null) {
            return List.of();
        } else {
            PlaylistsResponse playlistData = ApiClient.post(
                    ServerApiMeta.User.PLAYLIST,
                    new PagedRequestDataWithUID(profile.getUserId(), 50, 0),
                    loginInfo.loginCookieInfo.rawCookie()
            );
            return playlistData.getPlaylists();
        }
    }

    public LyricInfo getLyricInfo(MusicDetail musicDetail) {
        var response = ApiClient.post(ServerApiMeta.Music.WORD_BY_WORD_LYRIC, new IdRequest(musicDetail.getId()), loginApiService.randomVipCookieOr(loginApiService::getAnonymousCookie));
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

    public record GetAlbumDetailResult(AlbumInfo album, List<MusicDetail> songs) {
    }

    public record SearchAlbumsResult(List<AlbumInfo> albums) {
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

    public record PagedRequestData(int limit, int offset) {
    }
}