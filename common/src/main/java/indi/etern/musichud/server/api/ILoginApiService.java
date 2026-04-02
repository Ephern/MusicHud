package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.IServerEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
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

    void joinUnlogged(ServerPlayer serverPlayer);

    void logout(ServerPlayer player);

    void loginAsAnonymous(ServerPlayer player, boolean sendFail);

    void refreshAndSend(ServerPlayer player, LoginCookieInfo loginCookieInfo);

    LoginApiService.QRLoginData startQRLoginByPlayer(ServerPlayer player);

    Profile loadUserProfile(ServerPlayer player, LoginCookieInfo loginCookieInfo);

    void cancelQRLoginByPlayer(ServerPlayer player);

    LoginApiService.PlayerLoginInfo getLoginInfoByServerPlayer(ServerPlayer player);

    java.util.Map<ServerPlayer, LoginApiService.PlayerLoginInfo> getLoginedPlayerInfoMap();

    java.util.Set<java.util.function.Consumer<java.util.Map<ServerPlayer, LoginApiService.PlayerLoginInfo>>> getLoginStateChangeListeners();

    String getRawCookieOrElse(ServerPlayer serverPlayer, Supplier<String> supplier);

    @RegisterMark
    class Register implements ServerRegister {
        @Override
        public void register() {
            IServerEventService.getInstance().registerCommonPlayerQuit(player -> {
                getInstance(ApiProvider.NCM).logout(player);
            });
        }
    }
}
