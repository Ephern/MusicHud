package indi.etern.musichud.server.api.impl.ncm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.ModifyType;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.PostProcessable;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.server.api.UrlMeta;
import indi.etern.musichud.throwable.MusicResourceLoadingException;
import indi.etern.musichud.utils.IClientDistUtil;
import indi.etern.musichud.utils.JsonUtil;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.*;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MusicApiService implements IMusicApiService {
    private static final Logger logger = MusicHud.getLogger(MusicApiService.class);
    private static final Cache<Long, MusicDetail> musicDetailCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(400)
            .build();
    private static final Cache<Long, UserCategoryPlaylists> userPlaylistCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, LinkedHashSet<Artist>> userSubscribedArtistsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, LinkedHashSet<Album>> userSubscribedAlbumsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static volatile MusicApiService musicApiService;
    private final LoginApiService loginApiService = LoginApiService.getInstance();
    private final Gson gson = JsonUtil.gson;
    private final ConcurrentHashMap<IdAndUUIDKey, CompletableFuture<Playlist>> playlistDetailInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StringAndUUIDKey, CompletableFuture<List<MusicDetail>>> musicDetailsInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SearchKey, CompletableFuture<Object>> searchInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<LyricInfo>> lyricInfoInFlight = new ConcurrentHashMap<>();

    @SneakyThrows
    private static <K, T> T joinMerged(ConcurrentHashMap<K, CompletableFuture<T>> inFlight, K key, Supplier<T> loader) {
        CompletableFuture<T> future = inFlight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(loader, MusicHud.EXECUTOR));
        try {
            T result = future.get(10, TimeUnit.SECONDS);
            inFlight.remove(key, future);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlight.remove(key, future);
            throw e;
        } catch (Throwable e) {
            inFlight.remove(key, future);
            throw e;
        }
    }

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

    @SneakyThrows
    private static UserCategoryPlaylists loadUserCategoryPlaylist(long userId, LoginApiService.PlayerLoginInfo loginInfo) {
        CompletableFuture<Void> createdPlaylistFuture = new CompletableFuture<>();
        CompletableFuture<Void> subscribedPlaylistFuture = new CompletableFuture<>();
        UserCategoryPlaylists userCategoryPlaylists = new UserCategoryPlaylists();
        CompletableFuture<Void> totalComplete = CompletableFuture.allOf(createdPlaylistFuture, subscribedPlaylistFuture);
        MusicHud.EXECUTOR.submit(() -> {
            LinkedHashSet<Playlist> playlists = loadUserCreatedPlaylists(userId, loginInfo);
            userCategoryPlaylists.setLikeList(playlists.removeFirst());
            userCategoryPlaylists.setCreatedPlaylist(new ObservableSequencedSet<>(playlists));
            createdPlaylistFuture.complete(null);
        });
        MusicHud.EXECUTOR.submit(() -> {
            userCategoryPlaylists.setSubscribedPlaylist(new ObservableSequencedSet<>(loadUserSubscribedPlaylists(userId, loginInfo)));
            subscribedPlaylistFuture.complete(null);
        });
        totalComplete.get(5, TimeUnit.SECONDS);
        if (totalComplete.state() == Future.State.SUCCESS) {
            return userCategoryPlaylists;
        } else {
            return UserCategoryPlaylists.EMPTY;
        }
    }

    private static LinkedHashSet<Playlist> loadUserSubscribedPlaylists(long userId, LoginApiService.PlayerLoginInfo loginInfo) {
        PlaylistsResponse playlistData = ApiClient.post(
                ApiServerEndpointsMeta.User.SUBSCRIBED_PLAYLIST,
                new PagedRequestDataWithUID(userId, 100, 0),
                loginInfo.getLoginCookieInfo().rawCookie(),
                true);
        LinkedHashSet<Playlist> playlists = playlistData.data.playlists;
        return playlists == null ? new LinkedHashSet<>(0) : playlists.stream().filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
    }

    private static LinkedHashSet<Playlist> loadUserCreatedPlaylists(long userId, LoginApiService.PlayerLoginInfo loginInfo) {
        PlaylistsResponse playlistData = ApiClient.post(
                ApiServerEndpointsMeta.User.CREATED_PLAYLIST,
                new PagedRequestDataWithUID(userId, 100, 0),
                loginInfo.getLoginCookieInfo().rawCookie(),
                true);
        LinkedHashSet<Playlist> playlists = playlistData.data.playlists;
        return playlists == null ? new LinkedHashSet<>(0) : playlists.stream().filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
    }

    private static LinkedHashSet<Album> loadUserSubscribedAlbums(long userId, LoginApiService.PlayerLoginInfo loginInfo) {
        UserSubscribedAlbumResponse userSubscribedAlbumResponse = ApiClient.post(
                ApiServerEndpointsMeta.User.SUBSCRIBED_ALBUMS,
                new PagedRequestDataWithUID(userId, 50, 0),
                loginInfo.getLoginCookieInfo().rawCookie(),
                true);
        return userSubscribedAlbumResponse.data();
    }

    private static LinkedHashSet<Artist> loadUserSubscribedArtists(long userId, LoginApiService.PlayerLoginInfo loginInfo) {
        UserSubscribedArtistResponse userSubscribedArtistResponse = ApiClient.post(
                ApiServerEndpointsMeta.User.SUBSCRIBED_ARTISTS,
                new PagedRequestDataWithUID(userId, 50, 0),
                loginInfo.getLoginCookieInfo().rawCookie(),
                true);
        return userSubscribedArtistResponse.data();
    }

    private List<MusicDetail> appendArtistMusic(int offset, Artist artist, UUID playerUUID) {
        String rawCookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        GetArtistMusicResponse response = ApiClient.post(ApiServerEndpointsMeta.Artist.ALL_SONGS, new ArtistAllMusicRequest(artist.getId(), 50, offset, "time"), rawCookie, true);
        List<Long> musicDetailIds = response.songs.stream().map(MusicDetail::getId).toList();
        artist.setTotalMusicCount(response.total);
        List<MusicDetail> musicDetails = getMusicDetailByIds(musicDetailIds, null);
        artist.getMusicDetails().addAll(musicDetails);
        return musicDetails;
    }

    @Override
    @NonNull
    public Playlist getPlaylistDetail(long id, boolean ignoreCache, @Nullable UUID playerUUID) {
        try {
            Playlist cached = playlistsCache.getIfPresent(id);
            Playlist cachedUsed = ignoreCache ? null : cached;
            Playlist playlist;
            if (cachedUsed != null && !cachedUsed.getTracks().isEmpty()) {
                playlist = cachedUsed;
            } else {
                playlist = joinMerged(playlistDetailInFlight, new IdAndUUIDKey(id, playerUUID), () -> {
                    String rawCookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
                    PlaylistResponse playlistResponse = ApiClient.post(ApiServerEndpointsMeta.Playlist.DETAIL, new IdRequest(id), rawCookie, true);
                    if (playlistResponse.getCode() == 200) {
                        Playlist loaded = playlistResponse.getPlaylist();
                        if (cached != null) {
                            //To avoid dist crossing issues due to shared common caches in integrated server
                            cached.updateFrom(loaded, MusicHud.getCurrentEnvironment().getSide() == Environment.Side.SERVER || !IClientDistUtil.getInstance().inIntegratedServer());
                            return cached;
                        }
                        playlistsCache.put(id, loaded);
                        return loaded;
                    } else {
                        logger.error("Failed to get playlist detail of player: {} (response code: {})", Objects.requireNonNull(playerUUID), playlistResponse.getCode());
                        return Playlist.empty(id);
                    }
                });
            }
            LoginApiService.PlayerLoginInfo playerLoginInfo = playerUUID == null ? null : loginApiService.playerInfoMap.get(playerUUID);
            Profile profile = playerLoginInfo != null ? playerLoginInfo.getProfile() : null;
            if (playlist.getPrivacy() == Privacy.PRIVATE && !playlist.getCreator().equals(profile)) {
                return Playlist.privacyBlocked(id, playlist.getCreator());
            } else {
                return playlist;
            }
        } catch (Throwable e) {
            logger.error("Failed to load playlist detail: ", e);
            return Playlist.EMPTY;
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
        if (result != null && result.musicDetails() != null) {
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
            var key = new SearchKey(keywords, offset, limit, searchType);
            @SuppressWarnings("unchecked")
            T result = (T) joinMerged(searchInFlight, key, () -> {
                var requestBody = new SearchRequestBody(keywords, limit, offset, null, searchType);
                return ApiClient.post(ApiServerEndpointsMeta.Search.CLOUD, requestBody, null, true);
            });
            return transformer.apply((String) result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MusicDetail> getMusicDetailByIds(List<Long> ids, UUID playerUUID) {
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
        if (!uncachedIds.isEmpty()) {
            String cacheKey1 = uncachedIds.stream().sorted().map(String::valueOf)
                    .reduce("", (a, b) -> a + "," + b);
            List<MusicDetail> loaded = joinMerged(musicDetailsInFlight, new StringAndUUIDKey(cacheKey1, playerUUID), () -> {
                Object requestBody = new GetDetailsRequestBody(
                        String.join(",", uncachedIds.stream().map(String::valueOf).toList()), null);
                String userCookie = loginApiService.getRawCookieOrElse(playerUUID,
                        () -> loginApiService.randomVipCookieOrElse(loginApiService::getAnonymousCookie)
                );
                MusicDetailsResponse response = ApiClient.post(ApiServerEndpointsMeta.Music.DETAIL, requestBody, userCookie, true);
                List<MusicDetail> musicDetails = response.musicDetails();
                for (MusicDetail musicDetail : musicDetails) {
                    musicDetailCache.put(musicDetail.getId(), musicDetail);
                }
                return musicDetails;
            });
            result.addAll(loaded);
        }
        return result;
    }

    @Override
    @NonNull
    public Album getAlbumInfoDetail(long id, boolean ignoreCache, UUID playerUUID) {
        try {
            if (ignoreCache) {
                Album album = loadAlbumInfoDetail(id, playerUUID);
                Album previousAlbum = albumsCache.getIfPresent(id);
                if (previousAlbum != null) {
                    //To avoid dist crossing issues due to shared common caches in integrated server
                    previousAlbum.updateFrom(album, MusicHud.getCurrentEnvironment().getSide() == Environment.Side.SERVER || !IClientDistUtil.getInstance().inIntegratedServer());
                    return previousAlbum;
                } else {
                    albumsCache.put(id, album);
                    return album;
                }
            } else {
                Album cached = albumsCache.getIfPresent(id);
                if (cached != null) {
                    return cached;
                }
                Album album = loadAlbumInfoDetail(id, playerUUID);
                albumsCache.put(id, album);
                return album;
            }
        } catch (Throwable e) {
            logger.error("Failed to load playlist detail: ", e);
            return Album.NONE;
        }
    }

    @NotNull
    private Album loadAlbumInfoDetail(long id, UUID playerUUID) {
        String rawCookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        GetAlbumDetailResult post = ApiClient.post(ApiServerEndpointsMeta.Album.DETAIL, new IdRequest(id), rawCookie, true);
        Album album = post.album();
        post.songs.forEach(song -> {
            song.setAlbum(album.shallowCopyBriefInfo());// prevent loop reference
        });
        album.setMusicDetails(new ObservableSequencedSet<>(post.songs));
        return album;
    }

    @SneakyThrows
    @Override
    public Artist getArtistDetail(long id, UUID playerUUID) {
        return artistsCache.get(id,
                () -> {
                    String rawCookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
                    GetArtistDetailResponse post = ApiClient.post(ApiServerEndpointsMeta.Artist.DETAIL, new IdRequest(id), rawCookie, true);
                    GetArtistDetailResponseData data = post.data;
                    if (data != null) {
                        Artist artist = data.artist;
                        appendArtistMusic(0, artist, playerUUID);
                        return artist;
                    } else {
                        return Artist.UNKNOWN;
                    }
                }
        );
    }

    @SneakyThrows
    @Override
    public List<MusicDetail> getArtistMoreMusic(long id, int offset, UUID playerUUID) {
        Artist artist = getArtistDetail(id, playerUUID);
        return appendArtistMusic(offset, artist, playerUUID);
    }

    @SneakyThrows
    @Override
    public MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, UUID playerUUID) {
        boolean usingSubstitute = false;
        try {
            if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
                return MusicResourceInfo.NONE;
            } else {
                var loginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
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
                        logger.warn("Failed to load music resource for \"{}\"(id:{}), as resource url is not available", musicDetail.getName(), musicDetail.getId());
                        try {
                            usingSubstitute = true;
                            musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                            if (musicResourceInfo != MusicResourceInfo.NONE && ApiClient.checkUrlAvailable(musicResourceInfo.getUrl(), 5000)) {
                                completeLyricInfo(musicDetail);
                                return musicResourceInfo;
                            }
                        } catch (Exception ignored) {
                        }
                        logger.error("Failed to get resource for music from substitute as last trial: {} (ID: {})", musicDetail.getName(), musicDetail.getId());
                        return MusicResourceInfo.NONE;
                    }
                    var request = new GetDirectResourceUrlRequest(musicDetail.getId(), false, quality);
                    var response = ApiClient.post(ApiServerEndpointsMeta.Music.URL, request, cookie, true);
                    if (response.code == 200) {
                        musicResourceInfo = response.data.getFirst();
                        // 30 seconds trial or have no copyright
                        if (((extraInfo == null || !extraInfo.cloudSource())
                                && ((musicResourceInfo.getFee() == Fee.SEPARATELY_PURCHASE
                                || (musicResourceInfo.getFee() == Fee.VIP && !vipAccessible.get()))
                                || musicResourceInfo.getUrl() == null))
                        ) {
                            logger.warn("Failed to get resource for music: {} (ID: {}), trying substitute", musicDetail.getName(), musicDetail.getId());
                            usingSubstitute = true;
                            musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                        }
                        completeLyricInfo(musicDetail);
                    } else {
                        logger.warn("Failed to get resource for music: {} (ID: {}), trying substitute", musicDetail.getName(), musicDetail.getId());
                        try {
                            usingSubstitute = true;
                            musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                            completeLyricInfo(musicDetail);
                        } catch (Exception e) {
                            logger.error("Failed to get resource for music from substitute: {} (ID: {})", musicDetail.getName(), musicDetail.getId());
                            musicResourceInfo = MusicResourceInfo.NONE;
                        }
                    }
                    available = musicResourceInfo != MusicResourceInfo.NONE && ApiClient.checkUrlAvailable(musicResourceInfo.getUrl(), 5000);
                    retryCount++;
                } while (!available);
                return musicResourceInfo;
            }
        } catch (Throwable e) {
            if (e instanceof InterruptedException e1) {
                throw e1;
            }
            throw new MusicResourceLoadingException(e, musicDetail, usingSubstitute);
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

    private @NonNull MusicResourceInfo getMusicResourceInfoFromMatcher(MusicDetail musicDetail) {
        // see also: @neteasecloudmusicapienhanced/unblockmusic-utils
        // available sources (in /modules): baka bikonoo byfuns gdmusic msls qijieya unm whitisnot
        // in default, api enhanced will try all available sources, but recently all api are unstable
        var unblockRequest = new GetMatchResourceUrlRequest(musicDetail.getId(), null);
        var unblockResponse = ApiClient.post(ApiServerEndpointsMeta.Music.UNBLOCK, unblockRequest, loginApiService.randomVipCookieOrElse(null), true);
        if (unblockResponse.code == 200 && unblockResponse.data instanceof String url) {
            return MusicResourceInfo.from(url, musicDetail);
        } else {
            return MusicResourceInfo.NONE;
        }
    }

    @Override
    @SneakyThrows
    public UserCategoryPlaylists getPlayersUserPlaylists(boolean ignoreCache, UUID playerUUID) {
        if (playerUUID == null) {
            return UserCategoryPlaylists.EMPTY;
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        if (loginInfo == null || loginInfo.getProfile() == null) {
            return UserCategoryPlaylists.EMPTY;
        } else {
            long userId = loginInfo.getProfile().getUserId();
            if (ignoreCache) {
                UserCategoryPlaylists userCategoryPlaylists = loadUserCategoryPlaylist(userId, loginInfo);
                userPlaylistCache.put(userId, userCategoryPlaylists);
                return userCategoryPlaylists;
            } else {
                return userPlaylistCache.get(userId, () -> loadUserCategoryPlaylist(userId, loginInfo));
            }
        }
    }

    @Override
    @SneakyThrows
    public LinkedHashSet<Album> getPlayersUserSubscribedAlbums(boolean ignoreCache, UUID playerUUID) {
        if (playerUUID == null) {
            return new LinkedHashSet<>(0);
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        if (loginInfo == null || loginInfo.getProfile() == null) {
            return new LinkedHashSet<>(0);
        } else {
            long userId = loginInfo.getProfile().getUserId();
            if (ignoreCache) {
                LinkedHashSet<Album> albums = loadUserSubscribedAlbums(userId, loginInfo);
                userSubscribedAlbumsCache.put(userId, albums);
                return albums;
            } else {
                return userSubscribedAlbumsCache.get(userId, () -> loadUserSubscribedAlbums(userId, loginInfo));
            }
        }
    }

    @Override
    @SneakyThrows
    public LinkedHashSet<Artist> getPlayersUserSubscribedArtists(boolean ignoreCache, UUID playerUUID) {
        if (playerUUID == null) {
            return new LinkedHashSet<>(0);
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        if (loginInfo == null || loginInfo.getProfile() == null) {
            return new LinkedHashSet<>(0);
        } else {
            long userId = loginInfo.getProfile().getUserId();
            if (ignoreCache) {
                LinkedHashSet<Artist> artists = loadUserSubscribedArtists(userId, loginInfo);
                userSubscribedArtistsCache.put(userId, artists);
                return artists;
            }
            return userSubscribedArtistsCache.get(userId, () -> loadUserSubscribedArtists(userId, loginInfo));
        }
    }

    @Override
    public LyricInfo getLyricInfo(MusicDetail musicDetail) {
        return joinMerged(lyricInfoInFlight, musicDetail.getId(), () -> {
            var response = ApiClient.post(ApiServerEndpointsMeta.Music.WORD_BY_WORD_LYRIC, new IdRequest(musicDetail.getId()), loginApiService.randomVipCookieOrElse(loginApiService::getAnonymousCookie), true);
            if (response.getCode() == 200) {
                return response;
            } else {
                throw new RuntimeException("Failed to get lyric for music: " + musicDetail.getName() + " (ID: " + musicDetail.getId() + "), response code:" + response.getCode());
            }
        });
    }

    @Override
    public void addToPlaylist(long playlistId, long musicId, UUID playerUUID) {
        Playlist playlist = playlistsCache.getIfPresent(playlistId);
        ObservableSequencedSet.EditHandle<MusicDetail> musicDetailEditHandle = null;
        if (playlist != null) {
            ObservableSequencedSet<MusicDetail> musicDetails = playlist.getMusicDetails();
            musicDetailEditHandle = musicDetails.beginEdit();
            MusicDetail musicDetail = getMusicDetailByIds(List.of(musicId), playerUUID).getFirst();
            boolean contains = musicDetails.contains(musicDetail);
            if (!contains) {
                musicDetails.addFirst(musicDetail);
                playlist.setMusicTrackCount(playlist.getMusicTrackCount() + 1);
            }
        }
        try {
            ApiClient.post(ApiServerEndpointsMeta.Playlist.MODIFY_TRACKS,
                    new ModifyTracksRequest(ModifyType.ADD.getApiOperationName(), playlistId, String.valueOf(musicId)),
                    loginApiService.getLoginInfoByPlayerUUID(playerUUID).getLoginCookieInfo().rawCookie(),
                    true);
            if (musicDetailEditHandle != null) {
                musicDetailEditHandle.commit();
            }
        } catch (Exception e) {
            if (musicDetailEditHandle != null) {
                musicDetailEditHandle.rollback();
            }
            throw e;
        }
    }

    @Override
    public void removeFromPlaylist(long playlistId, long musicId, UUID playerUUID) {
        Playlist playlist = playlistsCache.getIfPresent(playlistId);
        ObservableSequencedSet.EditHandle<MusicDetail> musicDetailEditHandle = null;
        if (playlist != null) {
            MusicDetail cached = musicDetailCache.getIfPresent(musicId);
            ObservableSequencedSet<MusicDetail> musicDetails = playlist.getMusicDetails();
            musicDetailEditHandle = musicDetails.beginEdit();
            if (cached != null) {
                if (musicDetails.remove(cached)) {
                    playlist.setMusicTrackCount(playlist.getMusicTrackCount() - 1);
                }
            } else {
                musicDetails.stream().filter(m -> m.getId() == musicId).findFirst()
                        .ifPresent(musicDetail -> {
                                    musicDetails.remove(musicDetail);
                                    playlist.setMusicTrackCount(playlist.getMusicTrackCount() - 1);
                                }
                        );
            }
        }
        try {
            ApiClient.post(ApiServerEndpointsMeta.Playlist.MODIFY_TRACKS,
                    new ModifyTracksRequest(ModifyType.REMOVE.getApiOperationName(), playlistId, String.valueOf(musicId)),
                    loginApiService.getLoginInfoByPlayerUUID(playerUUID).getLoginCookieInfo().rawCookie(),
                    true);
            if (musicDetailEditHandle != null) {
                musicDetailEditHandle.commit();
            }
        } catch (Exception e) {
            if (musicDetailEditHandle != null) {
                musicDetailEditHandle.rollback();
            }
            throw e;
        }
    }

    @Override
    public void userSubscribe(long id, SubscribableType subscribableType, SubscribeAction action, UUID playerUUID) {
        Cache<Long, ?> cache;
        UrlMeta<?> meta = switch (subscribableType) {
            case ALBUM -> {
                cache = userSubscribedAlbumsCache;
                yield ApiServerEndpointsMeta.Album.MODIFY_SUBSCRIBE;
            }
            case ARTIST -> {
                cache = userSubscribedArtistsCache;
                yield ApiServerEndpointsMeta.Artist.MODIFY_SUBSCRIBE;
            }
            case PLAYLIST -> {
                cache = userPlaylistCache;
                yield ApiServerEndpointsMeta.Playlist.MODIFY_SUBSCRIBE;
            }
        };
        ApiClient.post(meta,
                new ModifySubscriptionRequest(String.valueOf(action.getCode()), id),
                loginApiService.getLoginInfoByPlayerUUID(playerUUID).getLoginCookieInfo().rawCookie(),
                true);
        cache.invalidate(id);
    }

    record IdAndUUIDKey(long id, UUID uuid) {
    }

    record StringAndUUIDKey(String string, UUID uuid) {
    }

    record IdRequest(long id) {
    }

    record ArtistAllMusicRequest(long id, int limit, int offset, String order/*hot|time*/) {
    }

    record SearchRequestBody(String keywords, int limit, int offset, String cookie, SearchType type) {
    }

    record SearchKey(String keywords, int offset, int limit, SearchType searchType) {
    }

    public record GetAlbumDetailResult(Album album, LinkedHashSet<MusicDetail> songs) {
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

    public record GetMatchResourceUrlResponse(int code, Object data) {
    }

    public record PagedRequestDataWithUID(long uid, int limit, int offset) {
    }

    public record PlaylistTracksResponse(LinkedHashSet<MusicDetail> songs) {
    }

    public record UserSubscribedAlbumResponse(LinkedHashSet<Album> data) {
    }

    public record UserSubscribedArtistResponse(LinkedHashSet<Artist> data) {
    }

    public record PlaylistsResponse(@SerializedName("data") Data data, int code) {
        public record Data(
                @SerializedName("subCount") int subCount,
                @SerializedName("playlist") LinkedHashSet<Playlist> playlists,
                @SerializedName("more") boolean hasMore,
                @SerializedName("count") int count) {
        }
    }

    public record MusicDetailsResponse(
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

    public record ModifyTracksRequest(
            @SerializedName("op")
            String operationType,
            @SerializedName("pid")
            long playlistId,
            @SerializedName("tracks")
            String tracks
    ) {
    }

    public record ModifySubscriptionRequest(
            @SerializedName("t")
            String operationType,
            @SerializedName("id")
            long beanId
    ) {
    }
}