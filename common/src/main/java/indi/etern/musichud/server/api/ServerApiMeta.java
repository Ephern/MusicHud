package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.MusicDetailsResponse;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.PlaylistResponse;
import indi.etern.musichud.beans.music.PlaylistsResponse;
import indi.etern.musichud.beans.user.AccountDetail;
import indi.etern.musichud.beans.user.UserDetail;
import indi.etern.musichud.interfaces.IEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("SpellCheckingInspection")
public class ServerApiMeta {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();

    public record UrlMeta<T>(String url, Set<String> requiredParams, Set<String> optionalParams, boolean noCache,
                             boolean anonymous, boolean autoRetry, Class<T> responseType) {
        @Override
        public @NotNull String toString() {
            return serverConfig.getServerApiBaseUrl() + url;
        }

        public URI toURI() {
            String uri = serverConfig.getServerApiBaseUrl() + url;
            List<String> query = new ArrayList<>();
            if (serverConfig.getUseRandomCnIp()) {
                //noinspection SpellCheckingInspection
                query.add("randomCNIP=true");
            }
            if (noCache) {
                query.add("timestamp=" + System.currentTimeMillis());
            }
            if (!query.isEmpty()) {
                uri += "?" + String.join("&", query);
            }
            return URI.create(uri);
        }
    }

    @RegisterMark
    public static class Register implements ServerRegister {
        private static Register register;
        private static final Logger ncmApiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/NCM-API");
        private static Process process;
        private static boolean continueRestart = true;
        @Getter
        private static BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
        @Getter
        private static final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
        private static final int maxTries = 5;
        private static int triedCount = 0;
        public enum BinaryApiServerStatus {
            STOPPED, LAUNCHING, RUNNING;

            public String i18nKey() {
                return MusicHud.MOD_ID + ".text.binaryApiServerStatus." + name();
            }
        }

        public void log(String s, boolean error) {
            if (error || s.contains("[ERROR]")) {
                ncmApiLogger.error(s.replace("[ERROR]", ""));
            } else {
                ncmApiLogger.debug(s.replace("[INFO]", ""));
            }
        }

        @Override
        public void register() {
            register = this;
            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("API Server Launcher");
                boolean apiAvailable = ApiClient.checkAvailable();
                if (serverConfig.getStartupBinaryApiServerWhenLaunch() && !apiAvailable) {
                    triedCount = 0;
                    startEmbeddedApiServer();
                    Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
                    IEventService eventService = IEventService.getInstance();
                    if (side == Environment.Side.CLIENT) {
                        eventService.registerClientLifecycleStopping(Register::stopApiServer);
                    } else if (side == Environment.Side.SERVER) {
                        eventService.registerServerLifecycleStopping(Register::stopApiServer);
                    }
                } else if (apiAvailable){
                    ncmApiLogger.info("API Server has been launched externally");
                }
            });
        }

        public static void stopApiServer() {
            if (process != null) {
                continueRestart = false;
                process.destroy();
            }
        }

        public static void restartApiServer() {
            if (register != null) {
                triedCount = 0;
                stopApiServer();
                register.startEmbeddedApiServer();
            }
        }

        private void startEmbeddedApiServer() {
            if (triedCount >= maxTries) {
                ncmApiLogger.error("Embedded API Server has been stopped due to maximum tries reached.");
                return;
            }
            String binaryExecutableApiServerPathString = serverConfig.getServerApiBinaryExecutablePath();
            Path binaryExecutableApiServerPath = Paths.get(binaryExecutableApiServerPathString);
            Path windowsExePath = Paths.get(binaryExecutableApiServerPathString + ".exe");
            boolean executable = Files.isExecutable(binaryExecutableApiServerPath) || Files.isExecutable(windowsExePath);
            boolean exists = Files.exists(binaryExecutableApiServerPath) || Files.exists(windowsExePath);
            if (exists) {
                if (executable) {
                    triedCount++;
                    try {
                        continueRestart = true;
                        setApiStatus(BinaryApiServerStatus.LAUNCHING);
                        process = Runtime.getRuntime().exec(new String[]{binaryExecutableApiServerPath.toString()});

                        // 使用虚拟线程池分别读取 stdout 和 stderr
                        MusicHud.EXECUTOR.execute(() -> {
                            Thread.currentThread().setName("API Console");
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.contains("Server started successfully") && binaryApiServerStatus == BinaryApiServerStatus.LAUNCHING) {
                                        setApiStatus(BinaryApiServerStatus.RUNNING);
                                        ncmApiLogger.info("Api server started");
                                    }
                                    log(line, false);
                                }
                            } catch (IOException e) {
                                ncmApiLogger.error("Error reading stdout", e);
                            }
                        });

                        MusicHud.EXECUTOR.execute(() -> {
                            Thread.currentThread().setName("API Console");
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    log(line, true);
                                }
                            } catch (IOException e) {
                                ncmApiLogger.error("Error reading stderr", e);
                            }
                        });

                        // 在另一个虚拟线程中等待进程结束，并处理重启逻辑
                        MusicHud.EXECUTOR.execute(() -> {
                            Thread.currentThread().setName("API Daemon");
                            try {
                                int exitCode = process.waitFor();
                                setApiStatus(BinaryApiServerStatus.STOPPED);
                                if (continueRestart) {
                                    ncmApiLogger.warn("Api server unexpectedly stopped with code:{}, restarting...", exitCode);
                                    startEmbeddedApiServer();
                                } else {
                                    ncmApiLogger.info("Api server stopped with code:{}", exitCode);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                ncmApiLogger.error("Process wait interrupted", e);
                            }
                        });
                    } catch (Exception e) {
                        MusicHud.LOGGER.error("Failed to call binary server at path: \"{}\"", binaryExecutableApiServerPathString, e);
                    }
                }
            }
        }

        private void setApiStatus(BinaryApiServerStatus status) {
            binaryApiServerStatus = status;
            apiStatusListeners.forEach(l -> l.accept(status));
        }
    }

    /**
     * Currently only QR code login and anonymous login are proved to be functional (2025/11/06)
     *
     */
    public static class Login {
        public static final UrlMeta<String> PHONE = new UrlMeta<>(
                "/login/cellphone",
                Set.of("phone", "md5_password"),
                Set.of("countrycode", "captcha"),
                false, false, true, String.class);
        public static final UrlMeta<String> EMAIL = new UrlMeta<>(
                "/login",
                Set.of("email", "md5_password"),
                null,
                false, false, true, String.class);
        public static final UrlMeta<LoginApiService.RefreshCookieResponse> REFRESH = new UrlMeta<>(
                "/login/refresh",
                null,
                null,
                false,
                false,
                true, LoginApiService.RefreshCookieResponse.class);
        public static final UrlMeta<LoginApiService.AnonymousLoginData> ANONYMOUS = new UrlMeta<>(
                "/register/anonimous",
                null,
                null,
                true,
                true,
                true, LoginApiService.AnonymousLoginData.class);
        public static final UrlMeta<String> STATUS = new UrlMeta<>("/login/status", null, null, true, false, true, String.class);
        public static final UrlMeta<String> LOGOUT = new UrlMeta<>("/logout", null, null, true, false, true, String.class);
        
        public static class QrCode {
            public static final UrlMeta<LoginApiService.QRLoginResponseInfo> KEY = new UrlMeta<>(
                    "/login/qr/key",
                    null,
                    null,
                    true,
                    false,
                    true, LoginApiService.QRLoginResponseInfo.class);
            public static final UrlMeta<LoginApiService.QRLoginData> GENERATE = new UrlMeta<>(
                    "/login/qr/create",
                    Set.of("key"),
                    Set.of("qrimg"),
                    true, false,
                    true, LoginApiService.QRLoginData.class);
            public static final UrlMeta<LoginApiService.QRLoginStatus> CHECK = new UrlMeta<>(
                    "/login/qr/check",
                    Set.of("key"),
                    null,
                    true, false,
                    false, LoginApiService.QRLoginStatus.class);
        }

        public static class DeviceCode {
            public static final UrlMeta<String> SENT = new UrlMeta<>(
                    "/captcha/sent",
                    Set.of("phone"),
                    Set.of("ctcode"),
                    false, false, true, String.class);
            public static final UrlMeta<String> VERIFY = new UrlMeta<>(
                    "/captcha/verify",
                    Set.of("phone", "captcha"),
                    Set.of("ctcode"),
                    true, false, true, String.class);
        }
    }

    public static class User {
        public static final UrlMeta<UserDetail> UID_DETAIL = new UrlMeta<>(
                "/user/detail",
                Set.of("uid"),
                null,
                true,
                false,
                true, UserDetail.class);
        public static final UrlMeta<AccountDetail> ACCOUNT = new UrlMeta<>(
                "/user/account",
                null,
                null,
                false,
                false,
                true, AccountDetail.class);
        public static final UrlMeta<String> SUBCOUNT = new UrlMeta<>("/user/subcount", null, null, true, false, true, String.class);
        public static final UrlMeta<String> LEVEL = new UrlMeta<>("/user/level", null, null, true, false, true, String.class);
        public static final UrlMeta<PlaylistsResponse> PLAYLIST = new UrlMeta<>(
                "/user/playlist",
                Set.of("uid"),
                Set.of("limit"/*default:30*/, "offset"),
                true,
                false,
                true, PlaylistsResponse.class);
        public static final UrlMeta<String> DJ = new UrlMeta<>(
                "/user/dj",
                Set.of("uid"),
                null,
                false, false, true, String.class);
        public static final UrlMeta<String> SUBSCRIBED_ARTISTS = new UrlMeta<>(
                "/artist/sublist",
                null,
                Set.of("limit"/*default:25*/, "offset"),
                true, false, true, String.class);
        public static final UrlMeta<String> SIBSCRIBED_TOPICS = new UrlMeta<>(
                "/topic/sublist",
                null,
                Set.of("limit"/*default:50*/, "offset"),
                false, false, true, String.class);
        public static final UrlMeta<String> SUBSCRIBED_ALBUMS = new UrlMeta<>(
                "/album/sublist",
                null,
                Set.of("limit"/*default:25*/, "offset"),
                true, false, true, String.class);
        public static final UrlMeta<String> RECENTLY_PLAYED = new UrlMeta<>(
                "/record/recent/song",
                null,
                Set.of("limit"/*default:100*/),
                true, false, true, String.class);
    }

    public static class Artist {
        public static final UrlMeta<String> DESCRIPTION = new UrlMeta<>(
                "/artist/desc",
                Set.of("id"),
                null,
                false, false, true, String.class);
        public static final UrlMeta<MusicApiService.GetArtistDetailResponse> DETAIL = new UrlMeta<>(
                "/artist/detail",
                Set.of("id"),
                null,
                true, false, true, MusicApiService.GetArtistDetailResponse.class);
        public static final UrlMeta<String> TOP50 = new UrlMeta<>(
                "/artist/top/song",
                Set.of("id"),
                null,
                false, false, true, String.class);
        public static final UrlMeta<MusicApiService.GetArtistMusicResponse> ALL_SONGS = new UrlMeta<>(
                "/artist/songs",
                Set.of("id"),
                Set.of("limit"/*default:50*/, "offset", "order"/* hot|time */),
                true, false, true, MusicApiService.GetArtistMusicResponse.class);
    }

    public static class Playlist {
        public static final UrlMeta<String> CATEGORIES = new UrlMeta<>("/playlist/catlist", null, null, false, false, true, String.class);
        public static final UrlMeta<String> HOT_CATEGORIES = new UrlMeta<>("/playlist/hot", null, null, false, false, true, String.class);
        public static final UrlMeta<String> HIGH_QUALITY_TAGS = new UrlMeta<>("/playlist/highquality/tags", null, null, false, false, true, String.class);

        public static final UrlMeta<String> NETIZEN_CREATIONS = new UrlMeta<>(
                "/top/playlist",
                null,
                Set.of("order"/* hot|time */, "cat", "limit"/*default:50*/, "offset"),
                false, false, true, String.class);
        public static final UrlMeta<String> HIGH_QUALITY = new UrlMeta<>(
                "/top/playlist/highquality",
                null,
                Set.of("cat", "limit"/*default:50*/, "before"),
                false,
                false,
                true
                , String.class);
        public static final UrlMeta<PlaylistResponse> DETAIL = new UrlMeta<>(
                "/playlist/detail",
                Set.of("id"),
                Set.of("s"/*subscribers counts default:8*/),
                true,
                false,
                true, PlaylistResponse.class);
        public static final UrlMeta<MusicApiService.PlaylistTracksResponse> ALL_SONGS = new UrlMeta<>(
                "/playlist/track/all",
                Set.of("id"),
                Set.of("limit"/*default:[all]*/, "offset"),
                true,
                false,
                true, MusicApiService.PlaylistTracksResponse.class);
    }

    public static class Music {
        public static final UrlMeta<MusicApiService.GetDirectResourceUrlResponse> URL = new UrlMeta<>(
                "/song/url/v1",
                Set.of("id", "unblock"/*true|false*/, "level"/* standard|higher|exhigh|lossless|hires|jyeffect|sky|dolby|jymaster */),
                null,
                true,
                false,
                true, MusicApiService.GetDirectResourceUrlResponse.class);
        public static final UrlMeta<String> CHECK = new UrlMeta<>(
                "/check/music",
                Set.of("id"),
                Set.of("br"/* 96000|128000|192000|256000|320000|999000 */),
                false, false, true, String.class);
        public static final UrlMeta<MusicApiService.GetMatchResourceUrlResponse> UNBLOCK = new UrlMeta<>(
                "/song/url/match",
                Set.of("id"),
                Set.of("source"/*pyncmd|bodian|kuwo|kugou|qq|migu*/),
                true, false, true, MusicApiService.GetMatchResourceUrlResponse.class);
        public static final UrlMeta<MusicDetailsResponse> DETAIL = new UrlMeta<>(
                "/song/detail",
                Set.of("ids"),
                null,
                true,
                false,
                true, MusicDetailsResponse.class);
        public static final UrlMeta<LyricInfo> LYRIC = new UrlMeta<>("/lyric",
                Set.of("id")
                , null,
                true, false, true, LyricInfo.class);
        public static final UrlMeta<LyricInfo> WORD_BY_WORD_LYRIC = new UrlMeta<>(
                "/lyric/new",
                Set.of("id"),
                null,
                true, false, true, LyricInfo.class);
    }

    public static class Album {
        public static final UrlMeta<MusicApiService.GetAlbumDetailResult> DETAIL = new UrlMeta<>(
                "/album",
                Set.of("id"),
                null,
                true, false, true, MusicApiService.GetAlbumDetailResult.class);
    }

    public static class Search {
        public static final UrlMeta<String> CLOUD = new UrlMeta<>(
                "/cloudsearch",
                Set.of("keywords"),
                Set.of("limit"/*default:30*/,
                        "offset",
                        "type"
                        /* 1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单, 1002: 用户, 1004: MV, 1006: 歌词, 1009: 电台, 1014: 视频, 1018:综合, 2000:声音 */),
                true,
                false,
                true, String.class);
        public static final UrlMeta<String> SUGGEST = new UrlMeta<>(
                "/search/suggest",
                Set.of("keywords"),
                Set.of("type"/*mobile*/),
                true,
                false,
                true, String.class);
    }
}
