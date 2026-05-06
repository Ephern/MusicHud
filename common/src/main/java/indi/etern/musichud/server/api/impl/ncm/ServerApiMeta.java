package indi.etern.musichud.server.api.impl.ncm;

import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.PlaylistResponse;
import indi.etern.musichud.server.api.UrlMeta;

import java.util.Set;

@SuppressWarnings("SpellCheckingInspection")
public class ServerApiMeta {
    /**
     * Currently only QR code login and anonymous login are proved to be functional (2025/11/06)
     *
     */
    public static class Login {
        public static final UrlMeta<LoginApiService.PhoneLoginResponse> PHONE = new UrlMeta<>(
                "/login/cellphone",
                Set.of("phone"),
                Set.of("md5_password", "captcha", "countrycode"),
                false,
                false,
                false,
                false,
                null,
                LoginApiService.PhoneLoginResponse.class);
        public static final UrlMeta<String> EMAIL = new UrlMeta<>(
                "/login",
                Set.of("email", "md5_password"),
                null,
                false,
                false,
                false,
                false,
                Set.of(200),
                String.class);
        public static final UrlMeta<LoginApiService.RefreshCookieResponse> REFRESH = new UrlMeta<>(
                "/login/refresh",
                null,
                null,
                false,
                false, false,
                true,
                Set.of(200),
                LoginApiService.RefreshCookieResponse.class);
        public static final UrlMeta<LoginApiService.AnonymousLoginData> ANONYMOUS = new UrlMeta<>(
                "/register/anonimous",
                null,
                null,
                true,
                false,
                true,
                true,
                Set.of(200),
                LoginApiService.AnonymousLoginData.class);
        public static final UrlMeta<String> STATUS = new UrlMeta<>(
                "/login/status",
                null,
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> LOGOUT = new UrlMeta<>(
                "/logout",
                null,
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);

        public static class QrCode {
            public static final UrlMeta<LoginApiService.QRLoginResponseInfo> KEY = new UrlMeta<>(
                    "/login/qr/key",
                    null,
                    null,
                    true,
                    false,
                    false,
                    true,
                    Set.of(200),
                    LoginApiService.QRLoginResponseInfo.class);
            public static final UrlMeta<LoginApiService.QRLoginData> GENERATE = new UrlMeta<>(
                    "/login/qr/create",
                    Set.of("key"),
                    Set.of("qrimg"),
                    true,
                    false,
                    false,
                    true,
                    Set.of(200),
                    LoginApiService.QRLoginData.class);
            public static final UrlMeta<LoginApiService.QRLoginStatus> CHECK = new UrlMeta<>(
                    "/login/qr/check",
                    Set.of("key"),
                    null,
                    true,
                    true,
                    false,
                    false,
                    Set.of(200),
                    LoginApiService.QRLoginStatus.class);
        }

        public static class DeviceCode {
            public static final UrlMeta<LoginApiService.SendValidationCodeResponse> SENT = new UrlMeta<>(
                    "/captcha/sent",
                    Set.of("phone"),
                    Set.of("ctcode"),
                    false,
                    false,
                    false,
                    true,
                    Set.of(200),
                    LoginApiService.SendValidationCodeResponse.class);
            public static final UrlMeta<String> VERIFY = new UrlMeta<>(
                    "/captcha/verify",
                    Set.of("phone", "captcha"),
                    Set.of("ctcode"),
                    true,
                    false,
                    false,
                    true,
                    Set.of(200),
                    String.class);
        }
    }

    public static class User {
        public static final UrlMeta<LoginApiService.ProfileResponse> UID_DETAIL = new UrlMeta<>(
                "/user/detail",
                Set.of("uid"),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                LoginApiService.ProfileResponse.class);
        public static final UrlMeta<LoginApiService.AccountDetail> ACCOUNT = new UrlMeta<>(
                "/user/account",
                null,
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                LoginApiService.AccountDetail.class);
        public static final UrlMeta<String> SUBCOUNT = new UrlMeta<>(
                "/user/subcount",
                null,
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> LEVEL = new UrlMeta<>(
                "/user/level",
                null,
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.PlaylistsResponse> PLAYLIST = new UrlMeta<>(
                "/user/playlist",
                Set.of("uid"),
                Set.of("limit"/*default:30*/, "offset"),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.PlaylistsResponse.class);
        public static final UrlMeta<String> DJ = new UrlMeta<>(
                "/user/dj",
                Set.of("uid"),
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.UserSubscribedArtistResponse> SUBSCRIBED_ARTISTS = new UrlMeta<>(
                "/artist/sublist",
                null,
                Set.of("limit"/*default:25*/, "offset"),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.UserSubscribedArtistResponse.class);
        public static final UrlMeta<String> SIBSCRIBED_TOPICS = new UrlMeta<>(
                "/topic/sublist",
                null,
                Set.of("limit"/*default:50*/, "offset"),
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.UserSubscribedAlbumResponse> SUBSCRIBED_ALBUMS = new UrlMeta<>(
                "/album/sublist",
                null,
                Set.of("limit"/*default:25*/, "offset"),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.UserSubscribedAlbumResponse.class);
        public static final UrlMeta<String> RECENTLY_PLAYED = new UrlMeta<>(
                "/record/recent/song",
                null,
                Set.of("limit"/*default:100*/),
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> CLOUD_DRIVE = new UrlMeta<>(
                "/user/cloud",
                null,
                Set.of("limit"/*default:30*/, "offset"),
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
    }

    public static class Artist {
        public static final UrlMeta<String> DESCRIPTION = new UrlMeta<>(
                "/artist/desc",
                Set.of("id"),
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.GetArtistDetailResponse> DETAIL = new UrlMeta<>(
                "/artist/detail",
                Set.of("id"),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.GetArtistDetailResponse.class);
        public static final UrlMeta<String> TOP50 = new UrlMeta<>(
                "/artist/top/song",
                Set.of("id"),
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.GetArtistMusicResponse> ALL_SONGS = new UrlMeta<>(
                "/artist/songs",
                Set.of("id"),
                Set.of("limit"/*default:50*/, "offset", "order"/* hot|time */),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.GetArtistMusicResponse.class);
    }

    public static class Playlist {
        public static final UrlMeta<String> CATEGORIES = new UrlMeta<>(
                "/playlist/catlist",
                null,
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> HOT_CATEGORIES = new UrlMeta<>(
                "/playlist/hot",
                null,
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> HIGH_QUALITY_TAGS = new UrlMeta<>(
                "/playlist/highquality/tags",
                null,
                null,
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> NETIZEN_CREATIONS = new UrlMeta<>(
                "/top/playlist",
                null,
                Set.of("order"/* hot|time */, "cat", "limit"/*default:50*/, "offset"),
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> HIGH_QUALITY = new UrlMeta<>(
                "/top/playlist/highquality",
                null,
                Set.of("cat", "limit"/*default:50*/, "before"),
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<PlaylistResponse> DETAIL = new UrlMeta<>(
                "/playlist/detail",
                Set.of("id"),
                Set.of("s"/*subscribers counts default:8*/),
                true,
                false,
                false,
                true,
                Set.of(200),
                PlaylistResponse.class);
        public static final UrlMeta<MusicApiService.PlaylistTracksResponse> ALL_SONGS = new UrlMeta<>(
                "/playlist/track/all",
                Set.of("id"),
                Set.of("limit"/*default:[all]*/, "offset"),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.PlaylistTracksResponse.class);
    }

    public static class Music {
        public static final UrlMeta<MusicApiService.GetDirectResourceUrlResponse> URL = new UrlMeta<>(
                "/song/url/v1",
                Set.of("id", "unblock"/*true|false*/, "level"/* standard|higher|exhigh|lossless|hires|jyeffect|sky|dolby|jymaster */),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.GetDirectResourceUrlResponse.class);
        public static final UrlMeta<String> CHECK = new UrlMeta<>(
                "/check/music",
                Set.of("id"),
                Set.of("br"/* 96000|128000|192000|256000|320000|999000 */),
                false,
                false,
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<MusicApiService.GetMatchResourceUrlResponse> UNBLOCK = new UrlMeta<>(
                "/song/url/match",
                Set.of("id"),
                Set.of("source"/*pyncmd|bodian|kuwo|kugou|qq|migu*/),
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.GetMatchResourceUrlResponse.class);
        public static final UrlMeta<MusicApiService.MusicDetailsResponse> DETAIL = new UrlMeta<>(
                "/song/detail",
                Set.of("ids"),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.MusicDetailsResponse.class);
        public static final UrlMeta<LyricInfo> LYRIC = new UrlMeta<>("/lyric",
                Set.of("id")
                , null,
                true,
                false,
                false,
                true,
                Set.of(200),
                LyricInfo.class);
        public static final UrlMeta<LyricInfo> WORD_BY_WORD_LYRIC = new UrlMeta<>(
                "/lyric/new",
                Set.of("id"),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                LyricInfo.class);
    }

    public static class Album {
        public static final UrlMeta<MusicApiService.GetAlbumDetailResult> DETAIL = new UrlMeta<>(
                "/album",
                Set.of("id"),
                null,
                true,
                false,
                false,
                true,
                Set.of(200),
                MusicApiService.GetAlbumDetailResult.class);
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
                false,
                true,
                Set.of(200),
                String.class);
        public static final UrlMeta<String> SUGGEST = new UrlMeta<>(
                "/search/suggest",
                Set.of("keywords"),
                Set.of("type"/*mobile*/),
                true,
                false,
                false,
                true,
                Set.of(200),
                String.class);
    }
}
