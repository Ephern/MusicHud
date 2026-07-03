package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import net.minecraft.world.entity.player.Player;

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

    void joinUnlogged(Player player);

    void logout(Player player);

    void loginAsAnonymous(Player player, boolean sendFail);

    void refreshAndSend(Player player, LoginCookieInfo loginCookieInfo);

    LoginApiService.QRLoginData startQRLoginByPlayer(Player player);

    Profile loadUserProfile(Player player, LoginCookieInfo loginCookieInfo);

    void cancelQRLoginByPlayer(Player player);

    LoginApiService.PlayerLoginInfo getLoginInfoByPlayer(Player player);

    Map<UUID, LoginApiService.PlayerLoginInfo> getPlayerInfoMap();

    Set<Consumer<Collection<LoginApiService.PlayerLoginInfo>>> getLoginStateChangeListeners();

    String getRawCookieOrElse(Player player, Supplier<String> supplier);

    void requestValidationCodeFor(int regionCode, long phone, Player player);

    void loginWithPhoneAndCode(int regionCode, long phone, int code, Player player);

    void loginWithPhoneAndPassword(long phone, String md5password, Player player);

    void loginWithEmailAndPassword(String email, String md5password, Player player);

    void loginWithCookie(LoginCookieInfo loginCookieInfo, boolean tryToRefresh, Player player);

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
}
