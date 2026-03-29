package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.AccountDetail;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.IEventService;
import indi.etern.musichud.interfaces.IntegerCodeEnum;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.*;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LoginApiService {
    private static final Logger logger = MusicHud.getLogger(LoginApiService.class);
    private static volatile LoginApiService loginApiService;
    private static final IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
    Map<ServerPlayer, Runnable> pollingMap = new HashMap<>();
    @Getter
    Map<ServerPlayer, PlayerLoginInfo> loginedPlayerInfoMap = new HashMap<>();
    @Getter
    Set<Consumer<Map<ServerPlayer, PlayerLoginInfo>>> loginStateChangeListeners = new HashSet<>();
    volatile String anonymousCookie;

    public static LoginApiService getInstance() {
        if (loginApiService == null) {
            synchronized (LoginApiService.class) {
                if (loginApiService == null) {
                    loginApiService = new LoginApiService();
                }
            }
        }
        return loginApiService;
    }

    private static void sendLoginFailResult(ServerPlayer player, Exception e) {
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

    public String getAnonymousCookie() {
        if (anonymousCookie == null) {
            synchronized (LoginApiService.class) {
                if (anonymousCookie == null) {
                    AnonymousLoginData response = ApiClient.post(
                            ServerApiMeta.Login.ANONYMOUS,
                            null,
                            null);
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

    public String randomVipCookieOr(Supplier<String> defaultCookieSupplier) {
        //noinspection ComparatorMethodParameterNotUsed
        Comparator<String> randomComparator = (a, b) -> MusicHud.RANDOM.nextInt(-1, 1);
        return loginedPlayerInfoMap.values().stream()
                .filter(info -> info.getVipType() != null && info.getVipType() == VipType.VIP)
                .map(info -> info.getLoginCookieInfo().rawCookie())
                .sorted(randomComparator)
                .findAny()
                .orElse(defaultCookieSupplier == null ? null : defaultCookieSupplier.get());
    }

    public void joinUnlogged(ServerPlayer serverPlayer) {
        loginedPlayerInfoMap.put(serverPlayer, PlayerLoginInfo.UNLOGGED);
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(loginedPlayerInfoMap));
        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(serverPlayer));
    }

    public void logout(ServerPlayer player) {
        Runnable remove = pollingMap.remove(player);
        loginedPlayerInfoMap.remove(player);
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(loginedPlayerInfoMap));
        if (remove != null) {
            logger.warn("Polling v-thread stopped as player {} quit", player.getName());
        }
        MusicPlayerServerService playerServerService = MusicPlayerServerService.getInstance();
        playerServerService.removeAllIdlePlaySource(player);
    }

    @SneakyThrows
    public void loginAsAnonymous(ServerPlayer player, boolean sendFail) {
        AnonymousLoginData response = ApiClient.post(
                ServerApiMeta.Login.ANONYMOUS,
                null,
                null);
        LoginCookieInfo loginCookieInfo;
        if (response.code == 200) {
            loginCookieInfo = new LoginCookieInfo(LoginType.ANONYMOUS, response.cookie, ZonedDateTime.now());
            AccountDetail accountDetail = loadUserProfile(player, loginCookieInfo);
            serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "", loginCookieInfo, accountDetail.getProfile()));
            MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(player));
        } else if (sendFail) {
            sendLoginFailResult(player, new RuntimeException("login failed"));
        }
    }

    @SneakyThrows
    public void refreshAndSend(ServerPlayer player, LoginCookieInfo loginCookieInfo) {
        RefreshCookieResponse cookieResponse = ApiClient.post(ServerApiMeta.Login.REFRESH, null, loginCookieInfo.rawCookie());
        LoginCookieInfo refreshedLoginCookieInfo;
        if (cookieResponse.code == 200) {
            refreshedLoginCookieInfo = new LoginCookieInfo(loginCookieInfo.type(), cookieResponse.cookie, ZonedDateTime.now());
            AccountDetail accountDetail = loadUserProfile(player, refreshedLoginCookieInfo);
            serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "", refreshedLoginCookieInfo, accountDetail.getProfile()));
        } else {
            AccountDetail accountDetail = loadUserProfile(player, loginCookieInfo);
            serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "warning: refresh cookie failed", loginCookieInfo, accountDetail.getProfile()));
            logger.warn("refresh for player \"{}\" failed, response code: {}", player.getName(), cookieResponse.code);
        }
    }

    @SneakyThrows
    public QRLoginData startQRLoginByPlayer(ServerPlayer player) {
        try {
            logger.debug("Start QR login by player: {}", player.getName());
            QRLoginResponseInfo response1 = ApiClient.get(
                    ServerApiMeta.Login.QrCode.KEY,
                    null
            );
            var requestBody = new QRLoginGenerateRequestInfo(response1.data.unikey, true);
            logger.debug("Got QR login key for player: {}", player.getName());
            QRLoginData response2 = ApiClient.post(
                    ServerApiMeta.Login.QrCode.GENERATE,
                    requestBody,
                    null
            );
            logger.debug("Got QR login code bitmap for player: {}", player.getName());

            startQRPollingVThread(player, response1.data.unikey);
            return response2;
        } catch (Exception e) {
            sendLoginFailResult(player, e);
            throw e;
        }
    }

    private void startQRPollingVThread(ServerPlayer player, String key) {
        var params2 = new QRLoginCheckRequestInfo(key);
        var ref = new Object() {
            Runnable runnable = null;
        };
        ref.runnable = () -> {
            Thread.currentThread().setName("PollingVWorker_" + Thread.currentThread().hashCode());
            try {
                logger.info("Start QR login polling v-thread for player: {}", player.getName());
                QRLoginStatus qrLoginStatus;
                do {
                    Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));

                    if (pollingMap.get(player) != ref.runnable) {
                        logger.warn("Polling v-thread stopped for player {}", player.getName());
                        return;
                    }

                    qrLoginStatus = ApiClient.post(
                            ServerApiMeta.Login.QrCode.CHECK,
                            params2,
                            null
                    );
                    logger.debug("QR login polling v-thread for {} got result: {}", player.getName(), qrLoginStatus.code);
                    if (qrLoginStatus.code == QRLoginStatus.Code.SUCCEED) {
                        logger.info("QR login polling v-thread pushing successful result to player: {}", player.getName());
                        LoginCookieInfo loginCookieInfo = new LoginCookieInfo(LoginType.QR_CODE, qrLoginStatus.cookie, ZonedDateTime.now());
                        AccountDetail accountDetail = loadUserProfile(player, loginCookieInfo);
                        serverNetworkService.sendToPlayer(player, new LoginResultMessage(true, "", loginCookieInfo, accountDetail.getProfile()));
                        MusicPlayerServerService.getInstance().sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(player));
                    }
                } while (qrLoginStatus.code != QRLoginStatus.Code.EXPIRED && qrLoginStatus.code != QRLoginStatus.Code.SUCCEED);
            } catch (InterruptedException e) {
                logger.warn("Thread ({}) interrupted while polling for QR login status", Thread.currentThread().getName(), e);
            } catch (Exception e) {
                sendLoginFailResult(player, e);
            }
        };
        pollingMap.put(player, ref.runnable);
        MusicHud.EXECUTOR.execute(ref.runnable);
    }

    public AccountDetail loadUserProfile(ServerPlayer player, LoginCookieInfo loginCookieInfo) {
        AccountDetail accountDetail = ApiClient.get(ServerApiMeta.User.ACCOUNT, loginCookieInfo.rawCookie());
        if (accountDetail.getProfile() == null) {
            if (accountDetail.getAccount().isAnonymous()) {
                accountDetail.setProfile(Profile.ANONYMOUS);
            } else {
                throw new IllegalStateException("accountDetail.profile is null but the account is not anonymous");
            }
        }
        PlayerLoginInfo playerLoginInfo = PlayerLoginInfo.of(loginCookieInfo);
        playerLoginInfo.appendAccountDetail(accountDetail);
        loginedPlayerInfoMap.put(player, playerLoginInfo);
        loginStateChangeListeners.forEach(mapConsumer -> mapConsumer.accept(loginedPlayerInfoMap));
        return accountDetail;
    }

    public void cancelQRLoginByPlayer(ServerPlayer player) {
        pollingMap.remove(player);
    }

    public PlayerLoginInfo getLoginInfoByServerPlayer(ServerPlayer player) {
        return loginedPlayerInfoMap.get(player);
    }

    @AllArgsConstructor
    @Getter
    public static class PlayerLoginInfo {
        public static final PlayerLoginInfo UNLOGGED = of(LoginCookieInfo.UNLOGGED);
        LoginCookieInfo loginCookieInfo;
        VipType vipType;
        Profile profile;

        public static PlayerLoginInfo of(LoginCookieInfo loginCookieInfo) {
            return new PlayerLoginInfo(loginCookieInfo, null, null);
        }

        public void appendAccountDetail(AccountDetail accountDetail) {
            this.profile = accountDetail.getProfile();
            vipType = accountDetail.getAccount().getVipType();
        }
    }

    public record AnonymousLoginData(int code, long userId, long createTime, String cookie) {
    }

    @RegisterMark
    public static class Register implements ServerRegister {
        @Override
        public void register() {
            IEventService.getInstance().registerCommonPlayerQuit(player -> {
                LoginApiService.getInstance().logout(player);
            });
        }
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
}