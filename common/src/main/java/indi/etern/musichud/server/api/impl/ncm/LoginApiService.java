package indi.etern.musichud.server.api.impl.ncm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.IntegerCodeEnum;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import indi.etern.musichud.network.payloads.requestResponseCycle.SendPhoneValidationCodeResponse;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.*;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LoginApiService implements ILoginApiService {
    private static final Logger logger = MusicHud.getLogger(LoginApiService.class);
    private static final IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
    private static volatile LoginApiService loginApiService;
    final Map<Player, Runnable> pollingMap = new HashMap<>();
    @Getter
    Map<UUID, PlayerLoginInfo> playerInfoMap = new HashMap<>();
    @Getter
    Set<Consumer<Collection<PlayerLoginInfo>>> loginStateChangeListeners = new HashSet<>();
    volatile String anonymousCookie;
    final Cache<Player, ZonedDateTime> lastSentTimes = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(Long.MAX_VALUE)
            .softValues()
            .build();

    public static LoginApiService getInstance() {
        if (LoginApiService.loginApiService == null) {
            synchronized (LoginApiService.class) {
                if (LoginApiService.loginApiService == null) {
                    LoginApiService.loginApiService = new LoginApiService();
                }
            }
        }
        return LoginApiService.loginApiService;
    }

    private static void sendSuccessLoginResultTo(Player player, LoginCookieInfo loginCookieInfo, Profile profile) {
        serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "", loginCookieInfo, profile));
        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(loginApiService.getLoginInfoByPlayer(player)));
    }

    void sendLoginFailResult(Player player, Exception e) {
        logger.error(e);
        String message;
        String eMessage = e.getMessage();
        message = e.getClass().getSimpleName() + (eMessage != null ? ":" + eMessage : "");
        serverNetworkService.sendToPlayer(player,
                new LoginResultMessage(
                        false,
                        message,
                        LoginCookieInfo.UNLOGGED,
                        Profile.ANONYMOUS)
        );
    }

    @Override
    public String getAnonymousCookie() {
        if (anonymousCookie == null) {
            synchronized (LoginApiService.class) {
                if (anonymousCookie == null) {
                    AnonymousLoginData response = ApiClient.post(
                            ServerApiMeta.Login.ANONYMOUS,
                            null,
                            null, true);
                    if (response.code == 200) {
                        anonymousCookie = response.cookie;
                    } else {
                        logger.warn("Failed to get an anonymous cookie");
                    }
                }
            }
        }
        return anonymousCookie;
    }

    @Override
    public String randomVipCookieOrElse(Supplier<String> defaultCookieSupplier) {
        //noinspection ComparatorMethodParameterNotUsed
        Comparator<String> randomComparator = (a, b) -> MusicHud.RANDOM.nextInt(-1, 1);
        return playerInfoMap.values().stream()
                .filter(info -> info.getVipType() != null && info.getVipType() == VipType.VIP)
                .map(info -> info.getLoginCookieInfo().rawCookie())
                .sorted(randomComparator)
                .findAny()
                .orElse(defaultCookieSupplier == null ? null : defaultCookieSupplier.get());
    }

    @Override
    public void joinUnlogged(Player player) {
        playerInfoMap.put(player.getUUID(), PlayerLoginInfo.of(player, LoginCookieInfo.UNLOGGED));
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(playerInfoMap.values()));
        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(loginApiService.getLoginInfoByPlayer(player)));
    }

    @Override
    public void logout(Player player) {
        Runnable remove = pollingMap.remove(player);
        playerInfoMap.remove(player);
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(playerInfoMap.values()));
        if (remove != null) {
            logger.warn("Polling v-thread stopped as player {} quit", player.getName());
        }
        MusicPlayerServerService playerServerService = MusicPlayerServerService.getInstance();
        playerServerService.removeAllIdlePlaySource(player);
    }

    @SneakyThrows
    @Override
    public void loginAsAnonymous(Player player, boolean sendFail) {
        try {
            AnonymousLoginData response = ApiClient.post(
                    ServerApiMeta.Login.ANONYMOUS,
                    null,
                    null, true);
            LoginCookieInfo loginCookieInfo;
            if (response.code == 200) {
                loginCookieInfo = new LoginCookieInfo(LoginType.ANONYMOUS, response.cookie, ZonedDateTime.now());
                Profile profile = loadUserProfile(player, loginCookieInfo);
                sendSuccessLoginResultTo(player, loginCookieInfo, profile);
            } else if (sendFail) {
                sendLoginFailResult(player, new RuntimeException("login failed"));
            }
        } catch (Exception e){
            if (sendFail) {
                sendLoginFailResult(player, new RuntimeException("login failed"));
            }
        }
    }

    @SneakyThrows
    @Override
    public void refreshAndSend(Player player, LoginCookieInfo loginCookieInfo) {
        RefreshCookieResponse cookieResponse = ApiClient.post(ServerApiMeta.Login.REFRESH, null, loginCookieInfo.rawCookie(), true);
        LoginCookieInfo refreshedLoginCookieInfo;
        if (cookieResponse.code == 200) {
            refreshedLoginCookieInfo = new LoginCookieInfo(loginCookieInfo.type(), cookieResponse.cookie, ZonedDateTime.now());
            Profile profile = loadUserProfile(player, refreshedLoginCookieInfo);
            serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "", refreshedLoginCookieInfo, profile));
        } else {
            Profile profile = loadUserProfile(player, loginCookieInfo);
            serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "warning: refresh cookie failed", loginCookieInfo, profile));
            logger.warn("refresh for player \"{}\" failed, response code: {}", player.getName(), cookieResponse.code);
        }
    }

    @SneakyThrows
    @Override
    public QRLoginData startQRLoginByPlayer(Player player) {
        try {
            logger.debug("Start QR login by player: {}", player.getName());
            QRLoginResponseInfo response1 = ApiClient.get(
                    ServerApiMeta.Login.QrCode.KEY,
                    null,
                    true);
            var requestBody = new QRLoginGenerateRequestInfo(response1.data.unikey, true);
            logger.debug("Got QR login key for player: {}", player.getName());
            QRLoginData response2 = ApiClient.post(
                    ServerApiMeta.Login.QrCode.GENERATE,
                    requestBody,
                    null,
                    true);
            logger.debug("Got QR login code bitmap for player: {}", player.getName());

            startQRPollingVThread(player, response1.data.unikey);
            return response2;
        } catch (Exception e) {
            sendLoginFailResult(player, e);
            throw e;
        }
    }

    private void startQRPollingVThread(Player player, String key) {
        var params2 = new QRLoginCheckRequestInfo(key);
        var ref = new Object() {
            Runnable runnable = null;
        };
        ref.runnable = () -> {
            Thread.currentThread().setName("MHWorker-Polling-V" + Thread.currentThread().hashCode());
            try {
                logger.info("Start QR login polling v-thread for player: {}", player.getName());
                QRLoginStatus qrLoginStatus = null;
                do {
                    Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));

                    if (pollingMap.get(player) != ref.runnable) {
                        logger.warn("Polling v-thread stopped for player {}", player.getName());
                        return;
                    }

                    try {
                        qrLoginStatus = ApiClient.post(
                                ServerApiMeta.Login.QrCode.CHECK,
                                params2,
                                null,
                                true);
                        logger.debug("QR login polling v-thread for {} got result: {}", player.getName(), qrLoginStatus.code);
                        if (qrLoginStatus.code == QRLoginStatus.Code.SUCCEED) {
                            logger.info("QR login polling v-thread pushing successful result to player: {}", player.getName());
                            LoginCookieInfo loginCookieInfo = new LoginCookieInfo(LoginType.QR_CODE, qrLoginStatus.cookie, ZonedDateTime.now());
                            Profile profile = loadUserProfile(player, loginCookieInfo);
                            sendSuccessLoginResultTo(player, loginCookieInfo, profile);
                        } else if (qrLoginStatus.code == QRLoginStatus.Code.EXPIRED) {
                            serverNetworkService.sendToPlayer(player,
                                    new LoginResultMessage(
                                            false,
                                            MusicHud.MOD_ID + ".text.login.qrExpired",
                                            LoginCookieInfo.UNLOGGED,
                                            Profile.ANONYMOUS));
                            logger.warn("QR code expired for player: {}", player.getName());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to check QR Status for player: {}", player.getName(), e);
                    }
                } while (qrLoginStatus == null || (qrLoginStatus.code != QRLoginStatus.Code.EXPIRED && qrLoginStatus.code != QRLoginStatus.Code.SUCCEED));
            } catch (InterruptedException e) {
                logger.warn("Thread ({}) interrupted while polling for QR login status", Thread.currentThread().getName(), e);
            } catch (Exception e) {
                sendLoginFailResult(player, e);
            }
            logger.info("Polling v-thread finished for player {}", player.getName());
        };
        pollingMap.put(player, ref.runnable);
        MusicHud.EXECUTOR.execute(ref.runnable);
    }

    @Override
    public Profile loadUserProfile(Player player, LoginCookieInfo loginCookieInfo) {
        AccountDetail accountDetail = ApiClient.get(ServerApiMeta.User.ACCOUNT, loginCookieInfo.rawCookie(), true);
        Profile profile = accountDetail.profile();
        return postProcessProfile(player, loginCookieInfo, profile, accountDetail.account);
    }

    private Profile postProcessProfile(Player player, LoginCookieInfo loginCookieInfo, Profile profile, Account account) {
        if (profile == null) {
            if (account.anonymous) {
                return Profile.ANONYMOUS;
            } else {
                throw new IllegalStateException("Profile is null but the account is not anonymous");
            }
        }
        profile.setVipType(account.vipType);
        PlayerLoginInfo playerLoginInfo = PlayerLoginInfo.of(player, loginCookieInfo);
        playerLoginInfo.appendProfile(profile);
        playerInfoMap.put(player.getUUID(), playerLoginInfo);
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(playerInfoMap.values()));
        return profile;
    }

    @Override
    public void cancelQRLoginByPlayer(Player player) {
        pollingMap.remove(player);
    }

    @Override
    public PlayerLoginInfo getLoginInfoByPlayer(Player player) {
        if (player == null) {
            return null;
        }
        return playerInfoMap.get(player.getUUID());
    }

    @Override
    public String getRawCookieOrElse(Player player, Supplier<String> supplier) {
        String rawCookie;
        if (player != null) {
            PlayerLoginInfo loginInfo = this.getPlayerInfoMap().get(player);
            if (loginInfo != null) {
                rawCookie = loginInfo.loginCookieInfo.rawCookie();
            } else {
                rawCookie = supplier != null ? supplier.get() : null;
            }
        } else {
            rawCookie = supplier != null ? supplier.get() : null;
        }
        return rawCookie;
    }

    @Override
    public void requestValidationCodeFor(int regionCode, long phone, Player player) {
        SendValidationCodeResponse response = ApiClient.post(ServerApiMeta.Login.DeviceCode.SENT, new ValidationCodeRequest(regionCode, phone), null, true);
        ZonedDateTime lastSentTime = lastSentTimes.getIfPresent(player);
        ZonedDateTime now = ZonedDateTime.now();

        Duration duration = null;
        if (lastSentTime != null) {
            duration = Duration.between(lastSentTime, now);
        }
        if (lastSentTime == null || duration.compareTo(Duration.ofSeconds(30)) > 0) {
            lastSentTimes.put(player, now);
            if (response.done) {
                logger.info("Successfully send code to player: {}", player.getName());
            } else {
                logger.error("Failed to send code to player: {}", player.getName());
            }
            serverNetworkService.sendToPlayer(player, new SendPhoneValidationCodeResponse(response.done, 30));
        } else {
            logger.warn("Refuse to send code to player: {}, as frequency limit", player.getName());
            serverNetworkService.sendToPlayer(player, new SendPhoneValidationCodeResponse(response.done, 30 - (int) duration.getSeconds()));
        }
    }

    @Override
    public void loginWithPhoneAndCode(int regionCode, long phone, int code, Player player) {
        PhoneCodeLoginRequest requestBody = new PhoneCodeLoginRequest(regionCode, phone, code);
        PhoneLoginResponse loginResponse = ApiClient.post(ServerApiMeta.Login.PHONE, requestBody, null, true);
        if (loginResponse.code == 200) {
            LoginCookieInfo loginCookieInfo = new LoginCookieInfo(LoginType.DEVICE_CODE, loginResponse.cookie, ZonedDateTime.now());
            Profile profile = postProcessProfile(player, loginCookieInfo, loginResponse.profile, loginResponse.account);
            sendSuccessLoginResultTo(player, loginCookieInfo, profile);
        } else {
            String i18nMessage = loginResponse.message;
            if (Objects.equals(i18nMessage, "验证码错误")) {
                i18nMessage = MusicHud.MOD_ID + ".text.validationCodeError";
            } else if (i18nMessage == null) {
                i18nMessage = MusicHud.MOD_ID + ".text.unknownError";
            }
            serverNetworkService.sendToPlayer(player,
                    new LoginResultMessage(
                            false,
                            i18nMessage,
                            LoginCookieInfo.UNLOGGED,
                            Profile.ANONYMOUS)
            );
        }
    }

    @Override
    public void loginWithPhoneAndPassword(long phone, String md5password, Player player) {
        throw new UnsupportedOperationException("Not supported yet due to api.");
    }

    @Override
    public void loginWithEmailAndPassword(String email, String md5password, Player player) {
        throw new UnsupportedOperationException("Not supported yet due to api.");
    }

    @Override
    public void disconnectToAll() {
        serverNetworkService.sendToPlayerInfos(playerInfoMap.values(), new ConnectResponse(false, Version.current, List.of(ApiProvider.NCM)));
    }

    @Override
    public void reconnectAll() {
        serverNetworkService.sendToPlayerInfos(playerInfoMap.values(), new ConnectResponse(true, Version.current, List.of(ApiProvider.NCM)));
    }

    record ValidationCodeRequest(int ctcode, long phone) {
    }

    @AllArgsConstructor
    @Getter
    public static class PlayerLoginInfo {
//        public static final PlayerLoginInfo UNLOGGED = of(null, LoginCookieInfo.UNLOGGED);
        LoginCookieInfo loginCookieInfo;
        Player player;
        VipType vipType;
        Profile profile;

        public static PlayerLoginInfo of(Player player, LoginCookieInfo loginCookieInfo) {
            return new PlayerLoginInfo(loginCookieInfo, player, null, null);
        }

        public void appendProfile(Profile profile) {
            this.profile = profile;
            vipType = profile.getVipType();
        }
    }

    public record AnonymousLoginData(int code, long userId, long createTime, String cookie) {
    }

    public record RefreshCookieResponse(
            String bizCode,
            int code,
            String cookie
    ) {
    }

    public record QRLoginData(int code, Data data) {
        public record Data(String qrurl, String qrimg) {
        }
    }

    public record QRLoginResponseInfo(int code, Data data) {
        private record Data(int code, String unikey) {
        }
    }

    private record QRLoginGenerateRequestInfo(String key, boolean qrimg) {
    }

    private record QRLoginCheckRequestInfo(String key) {
    }

    public record QRLoginStatus(Code code, String message, String cookie) {
        public enum Code implements IntegerCodeEnum {
            EXPIRED(800), PENDING(801), CONFIRMING(802), SUCCEED(803);
            @Getter
            public final int code;

            Code(int code) {
                this.code = code;
            }
        }
    }

    public record AccountDetail(Account account, Profile profile) {
    }

    public record ProfileResponse(Profile profile) {
    }

    public record SendValidationCodeResponse(@SerializedName("data") boolean done) {
    }

    @SuppressWarnings("SpellCheckingInspection")
    record PhoneCodeLoginRequest(int countrycode, long phone, @SerializedName("captcha") int code) {
    }

    public record PhoneLoginResponse(
            int code,
            Account account,
            Profile profile,
            String cookie,
            String message
    ) {
    }

    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Account {
        VipType vipType;
        @SuppressWarnings("SpellCheckingInspection")
        @SerializedName("anonimousUser")
        boolean anonymous;
    }
}