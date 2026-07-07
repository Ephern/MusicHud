package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ILoginApiService {
    static ILoginApiService getInstance(ApiProvider apiProvider) {
        if (Objects.requireNonNull(apiProvider) == ApiProvider.NCM) {
            return LoginApiService.getInstance();
        }
        throw new IllegalArgumentException("Invalid api provider");
    }

    String getAnonymousCookie();

    String randomVipCookieOrElse(Supplier<String> defaultCookieSupplier);

    void joinUnlogged(IPlayerClient player);

    void logout(IPlayerClient player);

    void loginAsAnonymous(IPlayerClient player, boolean sendFail);

    void refreshAndSend(IPlayerClient player, LoginCookieInfo loginCookieInfo);

    PusherInfo getPusherInfo(IPlayerClient player);

    QRLoginData startQRLoginByPlayer(IPlayerClient player);

    Profile loadUserProfile(IPlayerClient player, LoginCookieInfo loginCookieInfo);

    void cancelQRLoginByPlayer(IPlayerClient player);

    PlayerLoginInfo getLoginInfoByPlayerUUID(UUID playerUUID);

    Map<UUID, PlayerLoginInfo> getPlayerInfoMap();

    Set<Consumer<Collection<PlayerLoginInfo>>> getLoginStateChangeListeners();

    String getRawCookieOrElse(UUID playerUUID, Supplier<String> supplier);

    void requestValidationCodeFor(int regionCode, long phone, IPlayerClient player);

    void loginWithPhoneAndCode(int regionCode, long phone, int code, IPlayerClient player);

    void loginWithPhoneAndPassword(long phone, String md5password, IPlayerClient player);

    void loginWithEmailAndPassword(String email, String md5password, IPlayerClient player);

    void loginWithCookie(LoginCookieInfo loginCookieInfo, boolean tryToRefresh, IPlayerClient player);

    void disconnectToAll();

    void reconnectAll();

    @RegisterMark
    class Register implements ServerRegister {
        @Override
        public void register() {
            IServerEventService.getInstance().registerCommonPlayerQuit(player -> {
                MusicHud.EXECUTOR.execute(() -> {
                    getInstance(ApiProvider.NCM).logout(player);
                });
            });
        }
    }

    record QRLoginData(int code, Data data) {
        public record Data(String qrurl, String qrimg) {
        }
    }

    @AllArgsConstructor
    @Getter
    class PlayerLoginInfo {
        LoginCookieInfo loginCookieInfo;
        IPlayerClient player;
        VipType vipType;
        Profile profile;
        PusherInfo pusherInfo;

        public static PlayerLoginInfo of(IPlayerClient player, LoginCookieInfo loginCookieInfo) {
            return new PlayerLoginInfo(loginCookieInfo, player, null, null, PusherInfo.ofPlayer(player));
        }

        public void appendProfile(Profile profile) {
            this.profile = profile;
            vipType = profile.getVipType();
        }
    }
}
