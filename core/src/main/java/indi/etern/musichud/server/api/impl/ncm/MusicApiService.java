package indi.etern.musichud.server.api.impl.ncm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.ModifyType;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.beans.record.PlayRecord;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.beans.user.cloud.CloudTrackInfo;
import indi.etern.musichud.beans.user.cloud.CloudTracksPage;
import indi.etern.musichud.beans.user.cloud.UploadTaskMeta;
import indi.etern.musichud.interfaces.PostProcessable;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.server.api.UrlMeta;
import indi.etern.musichud.throwable.ApiException;
import indi.etern.musichud.throwable.MusicResourceLoadingException;
import indi.etern.musichud.throwable.PlaylistTypeUnsupportedException;
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
import java.util.stream.Collectors;

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

    @SneakyThrows(ExecutionException.class)
    private static <K, T> T joinMerged(ConcurrentHashMap<K, CompletableFuture<T>> inFlight, K key, Supplier<T> loader) throws InterruptedException, TimeoutException {
        CompletableFuture<T> future = inFlight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(loader, MusicHud.EXECUTOR));
        try {
            T result = future.get(20, TimeUnit.SECONDS);
            inFlight.remove(key, future);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            inFlight.remove(key, future);
            throw e;
        } catch (ExecutionException | TimeoutException e) {
            inFlight.remove(key, future);
            throw e;
        } catch (Throwable e) {
            inFlight.remove(key, future);
            throw new RuntimeException(e);
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
            try {
                LinkedHashSet<Playlist> playlists = loadUserCreatedPlaylists(userId, loginInfo);
                playlists.stream()
                        .filter(playlist ->
                                playlist.getCreator().getUserId() == userId
                                        && playlist.getSpecialType() == PlaylistSpecialType.LIKE_LIST)
                        .findFirst()
                        .ifPresentOrElse((playlist) -> {
                            userCategoryPlaylists.setLikeList(playlist);
                            playlists.remove(playlist);
                        }, () -> userCategoryPlaylists.setLikeList(Playlist.EMPTY));
                userCategoryPlaylists.setCreatedPlaylist(new ObservableSequencedSet<>(playlists));
                createdPlaylistFuture.complete(null);
            } catch (Exception e) {
                createdPlaylistFuture.completeExceptionally(e);
            }
        });
        MusicHud.EXECUTOR.submit(() -> {
            try {
                userCategoryPlaylists.setSubscribedPlaylist(new ObservableSequencedSet<>(loadUserSubscribedPlaylists(userId, loginInfo)));
            } catch (Exception e) {
                subscribedPlaylistFuture.completeExceptionally(e);
            }
            subscribedPlaylistFuture.complete(null);
        });
        totalComplete.get(5, TimeUnit.SECONDS);
        if (totalComplete.state() == Future.State.SUCCESS) {
            userPlaylistCache.put(userId, userCategoryPlaylists);
            return userCategoryPlaylists;
        } else {
            throw new RuntimeException("Failed to load user playlists");
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

    private static String describe(CodeMessage response) {
        if (response == null) {
            return "empty response";
        }
        return response.code() + (response.msg() == null || response.msg().isBlank() ? "" : " " + response.msg());
    }

    private List<MusicDetail> appendArtistMusic(int offset, Artist artist, UUID playerUUID) throws InterruptedException, TimeoutException {
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
            ILoginApiService.PlayerLoginInfo userLoginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
            Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(id, -1));
            long userId = userLoginInfo == null ? -1 : (userLoginInfo.getProfile() instanceof Profile profile ? profile.getUserId() : -1);
            if ((cached == null || cached.getSpecialType() == PlaylistSpecialType.USER_SPECIFIC) && userId != -1) {
                cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(id, userId));
            }
            Playlist finalCached = cached;
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
                        if (finalCached != null) {
                            //To avoid dist crossing issues due to shared common caches in integrated server
                            finalCached.updateFrom(loaded, MusicHud.getCurrentEnvironment().getSide() == Environment.Side.SERVER || !IClientDistUtil.getInstance().inIntegratedServer());
                            return finalCached;
                        }
                        if (loaded.getSpecialType() == PlaylistSpecialType.USER_SPECIFIC) {
                            playlistsCache.put(PlaylistCacheKey.of(loaded, userId), loaded);
                        } else {
                            playlistsCache.put(PlaylistCacheKey.of(loaded, -1), loaded);
                        }
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
    public List<MusicDetail> getMusicDetailByIds(List<Long> ids, UUID playerUUID) throws InterruptedException, TimeoutException {
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
                boolean isVip = loginInfo != null && (loginInfo.getVipType() == VipType.VIP || loginInfo.getVipType() == VipType.SVIP);
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
                        logger.warn("Failed to load music resource for \"{}\"(id:{}), as resource url is not available, trying substitute", musicDetail.getName(), musicDetail.getId());
                        try {
                            usingSubstitute = true;
                            musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                            if (musicResourceInfo != MusicResourceInfo.NONE && ApiClient.checkUrlAvailable(musicResourceInfo.getUrl(), 5000)) {
                                logger.info("Succeed to get resource for music: {} (ID: {}) from substitute", musicDetail.getName(), musicDetail.getId());
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
                            if (musicResourceInfo == MusicResourceInfo.NONE) {
                                return MusicResourceInfo.NONE;
                            }
                            logger.info("Succeed to get resource for music: {} (ID: {}) from substitute", musicDetail.getName(), musicDetail.getId());
                        }
                        completeLyricInfo(musicDetail);
                    } else {
                        logger.warn("Failed to get resource for music: {} (ID: {}), trying substitute", musicDetail.getName(), musicDetail.getId());
                        try {
                            usingSubstitute = true;
                            musicResourceInfo = getMusicResourceInfoFromMatcher(musicDetail);
                            if (musicResourceInfo == MusicResourceInfo.NONE) {
                                return MusicResourceInfo.NONE;
                            }
                            logger.info("Succeed to get resource for music: {} (ID: {}) from substitute", musicDetail.getName(), musicDetail.getId());
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
            throw new IllegalArgumentException("player uuid is null");
        }
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        if (loginInfo == null || loginInfo.getProfile() == null) {
            throw new IllegalStateException("player have not login yet");
        } else {
            long userId = loginInfo.getProfile().getUserId();
            if (ignoreCache) {
                return loadUserCategoryPlaylist(userId, loginInfo);
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
                if (albums == null) {
                    throw new RuntimeException("Failed to load user subscribed albums");
                }
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
                if (artists == null) {
                    throw new RuntimeException("Failed to load user subscribed artists");
                }
                userSubscribedArtistsCache.put(userId, artists);
                return artists;
            }
            return userSubscribedArtistsCache.get(userId, () -> loadUserSubscribedArtists(userId, loginInfo));
        }
    }

    @Override
    public LyricInfo getLyricInfo(MusicDetail musicDetail) throws InterruptedException, TimeoutException {
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
    public void addToPlaylist(long playlistId, long musicId, UUID playerUUID) throws InterruptedException, TimeoutException {
        ILoginApiService.PlayerLoginInfo userLoginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlistId, -1));
        if (cached == null) {
            long userId = userLoginInfo == null ? -1 : (userLoginInfo.getProfile() instanceof Profile profile ? profile.getUserId() : -1);
            if (userId != -1) {
                cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlistId, userId));
            }
        }
        ObservableSequencedSet.EditHandle<MusicDetail> musicDetailEditHandle = null;
        if (cached != null) {
            ObservableSequencedSet<MusicDetail> musicDetails = cached.getMusicDetails();
            musicDetailEditHandle = musicDetails.beginEdit();
            MusicDetail musicDetail = getMusicDetailByIds(List.of(musicId), playerUUID).getFirst();
            boolean contains = musicDetails.contains(musicDetail);
            if (!contains) {
                musicDetails.addFirst(musicDetail);
                cached.setMusicTrackCount(cached.getMusicTrackCount() + 1);
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
        refreshPlaylistCacheAfterModify(playlistId, playerUUID);
    }

    @Override
    public void removeFromPlaylist(long playlistId, long musicId, UUID playerUUID) {
        ILoginApiService.PlayerLoginInfo userLoginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
        Playlist playlist = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlistId, -1));
        if (playlist == null) {
            long userId = userLoginInfo == null ? -1 : (userLoginInfo.getProfile() instanceof Profile profile ? profile.getUserId() : -1);
            if (userId != -1) {
                playlist = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlistId, userId));
            }
        }
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
                Playlist finalPlaylist = playlist;
                musicDetails.stream().filter(m -> m.getId() == musicId).findFirst()
                        .ifPresent(musicDetail -> {
                                    musicDetails.remove(musicDetail);
                                    finalPlaylist.setMusicTrackCount(finalPlaylist.getMusicTrackCount() - 1);
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
        refreshPlaylistCacheAfterModify(playlistId, playerUUID);
    }

    private void refreshPlaylistCacheAfterModify(long playlistId, UUID playerUUID) {
        Playlist refreshed = getPlaylistDetail(playlistId, true, playerUUID);
        if (refreshed == Playlist.EMPTY || refreshed.getMusicDetails().isEmpty()) {
            ILoginApiService.PlayerLoginInfo userLoginInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
            long userId = userLoginInfo == null ? -1 : (userLoginInfo.getProfile() instanceof Profile profile ? profile.getUserId() : -1);
            if (userId != -1) {
                playlistsCache.invalidate(CommonCaches.PlaylistCacheKey.of(playlistId, userId));
            }
            playlistsCache.invalidate(CommonCaches.PlaylistCacheKey.of(playlistId));
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

    @Override
    public void scrobble(long musicId, int playedInSecond, int durationInSecond, int bitrate, Quality quality,
                         @Nullable SourceMeta source, UUID playerUUID) {
        if (playerUUID != null) {
            ILoginApiService.PlayerLoginInfo loginInfoByPlayerUUID = loginApiService.getLoginInfoByPlayerUUID(playerUUID);
            if (loginInfoByPlayerUUID != null) {
                String s = ApiClient.post(
                        ApiServerEndpointsMeta.User.SCROBBLE,
                        new ScrobbleRequest(musicId, playedInSecond, durationInSecond, bitrate, quality.getAlias(),
                                source == null ? null : source.id(),
                                source == null ? null : source.sourceTypeName()),
                        loginInfoByPlayerUUID.getLoginCookieInfo().rawCookie(),
                        true);
                logger.debug("Scrobble result:\n{}", s);
            }
        }
    }

    @Override
    public List<MusicDetail> getIntelligentList(long musicId, long playlistId, @Nullable Long sid, @Nullable UUID playerUUID) {
        String rawCookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        IntelligentListResponse response = ApiClient.post(
                ApiServerEndpointsMeta.Playlist.INTELLIGENT_LIST,
                new IntelligentListRequest(musicId, playlistId, sid),
                rawCookie,
                true);
        if (response.code() == 200) {
            if (response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .map(IntelligentItem::songInfo)
                    .filter(Objects::nonNull)
                    .toList();
        } else if (response.code() == 400) {
            throw new PlaylistTypeUnsupportedException();
        }
        throw new ApiException("getIntelligentList failed with code " + response.code()
                + (response.message() == null ? "" : ": " + response.message()));
    }

    @Override
    public List<? extends PlayRecord<?>> getUserPlayRecords(PlayRecord.ResourceType type, UUID playerUUID) {
        LoginCookieInfo loginCookieInfo = loginApiService.getLoginInfoByPlayerUUID(playerUUID).getLoginCookieInfo();
        if (loginCookieInfo.type() == LoginType.ANONYMOUS) {
            throw new IllegalStateException("Anonymous user");
        }
        RecentRecordResponse<?> playRecords = switch (type) {
            case SONG -> ApiClient.post(
                    ApiServerEndpointsMeta.User.RECENT_TRACK,
                    new RecentRecordRequest(300),
                    loginCookieInfo.rawCookie(),
                    true
            );
            case PLAYLIST -> ApiClient.post(
                    ApiServerEndpointsMeta.User.RECENT_PLAYLIST,
                    new RecentRecordRequest(300),
                    loginCookieInfo.rawCookie(),
                    true
            );
            case ALBUM -> ApiClient.post(
                    ApiServerEndpointsMeta.User.RECENT_ALBUM,
                    new RecentRecordRequest(300),
                    loginCookieInfo.rawCookie(),
                    true
            );
            case UNSET -> throw new IllegalStateException("Invalid type");
        };
        return playRecords.data.list;
    }

    @Override
    public UploadTaskMeta requestUploadUrl(String fileName, String md5, long fileBytes, UUID playerUUID) {
        String cookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        UploadTokenResponse response = ApiClient.post(ApiServerEndpointsMeta.User.Cloud.NEW_UPLOAD_TASK,
                new UploadTokenRequest(cookie, md5, fileBytes, fileName), cookie, true);
        if (response == null || response.code != 200) {
            throw new ApiException("Cloud upload token request failed: " + describe(response));
        }
        UploadTokenData data = response.data;
        if (data == null) {
            throw new ApiException("Cloud upload token response missing data");
        }
        if (data.songId == null || data.uploadUrl == null || data.uploadUrl.isBlank()) {
            throw new ApiException("Cloud upload token response incomplete");
        }
        return new UploadTaskMeta(true, data.uploadUrl,
                data.uploadToken == null ? "" : data.uploadToken, data.needUpload,
                data.songId, data.resourceId == null ? "" : data.resourceId, fileBytes, md5, fileName);
    }

    @Override
    public CloudTracksPage getUserCloudTracks(int offset, int limit, UUID playerUUID) {
        String cookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        CloudListResponse response = ApiClient.post(ApiServerEndpointsMeta.User.Cloud.LIST,
                new CloudListRequest(limit, offset), cookie, true);
        if (response == null || response.data() == null) {
            return CloudTracksPage.EMPTY;
        }
        List<CloudTrackInfo> tracks = new ArrayList<>(response.data().size());
        for (CloudItem item : response.data()) {
            if (item == null || item.simpleSong() == null) continue;
            MusicDetail detail = item.simpleSong();
            if (detail.getId() == 0) continue;
            // Mark the track as a cloud source so playback uses the user's own cookie,
            // and seed the detail cache so later playback resolves the same instance.
            detail.setExtraInfo(new MusicDetail.ExtraInfo(true, 0, false));
            musicDetailCache.put(detail.getId(), detail);
            CloudAudioFoleMeta privateCloud = item.privateCloud();
            String md5 = privateCloud == null || privateCloud.md5() == null || privateCloud.md5().isBlank()
                    ? null : privateCloud.md5().toLowerCase(Locale.ROOT);
            long fileSize = privateCloud == null ? 0 : privateCloud.fileSize();
            tracks.add(new CloudTrackInfo(detail, md5, fileSize));
        }
        return new CloudTracksPage(tracks, response.count() > 0 ? response.count() : tracks.size(), response.usedBytes, response.maxBytes);
    }

    @Override
    public void deleteCloudTracks(List<Long> ids, UUID playerUUID) {
        if (ids == null || ids.isEmpty()) return;
        String cookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        String idParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        CodeAndMessageResponse response = ApiClient.post(ApiServerEndpointsMeta.User.Cloud.DELETE,
                new CloudDeleteRequest(idParam), cookie, true);
        if (response == null || response.code() != 200) {
            throw new ApiException("Failed to delete cloud tracks: " + describe(response));
        }
    }

    @Override
    public String completeCloudUpload(String songId, String resourceId, String md5, String fileName,
                                      String song, String artist, String album, UUID playerUUID) {
        String cookie = loginApiService.getRawCookieOrElse(playerUUID, loginApiService::getAnonymousCookie);
        CompleteUploadResponse response = ApiClient.post(ApiServerEndpointsMeta.User.Cloud.COMPLETE_UPLOADED_META,
                new CompleteUploadRequest(cookie, songId, resourceId, md5, fileName, song, artist, album), cookie, true);
        if (response == null || response.code() != 200) {
            throw new ApiException("Failed to complete cloud upload: " + describe(response));
        }
        CompleteUploadData data = response.data();
        return data == null ? null : data.songId();
    }

    /**
     * Common shape of the NCM API envelope; lets {@link #describe(CodeMessage)} handle them uniformly.
     */
    public interface CodeMessage {
        int code();

        String msg();
    }

    public
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

    public record ScrobbleRequest(
            long id,
            @SerializedName("time")
            int playedInSecond,
            @SerializedName("total")
            int durationInSecond,
            int bitrate,
            @SerializedName("level")
            String quality,
            @SerializedName("sourceid")
            Long sourceId,
            @SerializedName("source")
            String source
    ) {
    }

    public record IntelligentListRequest(long id/*musicId*/, long pid/*playlistId*/, Long sid/*startId*/) {
    }

    public record IntelligentItem(long id, String alg, boolean recommended, MusicDetail songInfo) {
    }

    public record IntelligentListResponse(int code, String message, List<IntelligentItem> data) {
    }

    public record RecentRecordRequest(int limit) {
    }

    public record RecentRecordResponse<T>(Data<T> data) {
        public record Data<T>(int total, List<PlayRecord<T>> list) {
        }
    }

    record UploadTokenRequest(String cookie, String md5, long fileSize, String filename) {
    }

    record CloudListRequest(int limit, int offset) {
    }

    public record CloudListResponse(int code, String msg, List<CloudItem> data, int count,
                                    @SerializedName("size") long usedBytes,
                                    @SerializedName("maxSize") long maxBytes) implements CodeMessage {
    }

    /**
     * A single /user/cloud entry. Only the embedded song detail is needed here; the
     * private cloud song id always equals {@code simpleSong.id}, so deletion can work
     * off the returned track id without keeping a separate mapping.
     */
    public record CloudItem(String songName, MusicDetail simpleSong,
                            @SerializedName("privateCloud") CloudAudioFoleMeta privateCloud) {
    }

    public record CloudAudioFoleMeta(String md5, long fileSize) {
    }

    record CloudDeleteRequest(String id) {
    }

    record CompleteUploadRequest(String cookie, String songId, String resourceId, String md5, String filename,
                                 String song, String artist, String album) {
    }

    public record CodeAndMessageResponse(int code, String msg) implements CodeMessage {
    }

    public record UploadTokenResponse(int code, String msg, UploadTokenData data) implements CodeMessage {
    }

    public record UploadTokenData(boolean needUpload, String songId, String uploadToken, String uploadUrl,
                                  String resourceId, @SerializedName("objectKey") String objectKey,
                                  String md5, long fileSize, String filename) {
    }

    public record CompleteUploadResponse(int code, String msg, CompleteUploadData data) implements CodeMessage {
    }

    public record CompleteUploadData(String songId) {
    }
}