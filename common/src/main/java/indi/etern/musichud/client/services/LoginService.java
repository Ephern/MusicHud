package indi.etern.musichud.client.services;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.components.AccountView;
import indi.etern.musichud.client.ui.components.QRLoginView;
import indi.etern.musichud.client.ui.pages.AccountBaseView;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.pushMessages.c2s.LogoutMessage;
import indi.etern.musichud.network.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.network.requestResponseCycle.AnonymousLoginRequest;
import indi.etern.musichud.network.requestResponseCycle.ConnectRequest;
import indi.etern.musichud.network.requestResponseCycle.CookieLoginRequest;
import indi.etern.musichud.network.requestResponseCycle.StartQRLoginResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LoginService {
    private static volatile LoginService instance = null;
    @Setter
    private Consumer<StartQRLoginResponse> loginResponseHandler;
    @Getter
    private final List<Consumer<LoginCookieInfo>> loginCompleteListeners = new ArrayList<>();
    @Getter
    NetworkManager.NetworkReceiver<StartQRLoginResponse> qrLoginResponseReceiver = (qrLoginResponse, context) -> {
        if (loginResponseHandler != null)
            loginResponseHandler.accept(qrLoginResponse);
    };
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    @Getter
    NetworkManager.NetworkReceiver<LoginResultMessage> loginResultReceiver = (loginResult, context) -> {
        MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("Login Processor");
            LoginCookieInfo loginCookieInfo = loginResult.loginCookieInfo();
            LoginType type = loginCookieInfo.type();
            if (type != LoginType.UNLOGGED && type != LoginType.ANONYMOUS && loginResult.success()) {
                loginCookieInfo.setToClientCookie();
                Profile.setCurrent(loginResult.profile());
                loginCompleteListeners.forEach(c -> c.accept(loginCookieInfo));
            } else if (type == LoginType.ANONYMOUS) {
                loginCookieInfo.setToClientCookie();
                Profile.setCurrent(Profile.ANONYMOUS);
                loginCompleteListeners.forEach(c -> c.accept(loginCookieInfo));
            } else {
                logger.warn("Login failed");
            }
            AccountBaseView accountBaseView = AccountBaseView.getInstance();
            if (accountBaseView != null) {
                MuiModApi.postToUiThread(accountBaseView::refresh);
                if (loginResult.success()) {
                    ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
                    profileConfigData.setProfile(loginResult.profile());
                    profileConfigData.saveToConfig();
                } else {
                    MuiModApi.postToUiThread(() -> {
                        AccountView accountView = AccountView.getInstance();
                        if (accountView != null) {
                            accountView.refresh();
                        }
                        QRLoginView qrLoginView = QRLoginView.getInstance();
                        if (qrLoginView != null) {
                            qrLoginView.reset();
                            qrLoginView.errorText(loginResult.message());
                        }
                    });
                }
            }
        });
    };

    public static LoginService getInstance() {
        if (instance == null) {
            synchronized (LoginService.class) {
                if (instance == null) {
                    instance = new LoginService();
                }
            }
        }
        return instance;
    }

    public boolean isLogined() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        return loginCookieInfo.type() != LoginType.UNLOGGED &&
                loginCookieInfo.type() != LoginType.ANONYMOUS &&
                MusicHud.getStatus() == MusicHud.ConnectStatus.CONNECTED;
    }

    public void logout() {
        NetworkManager.sendToServer(LogoutMessage.MESSAGE);
    }

    @RegisterMark
    public static final class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            ClientPlayerEvent.CLIENT_PLAYER_JOIN.register((player) -> {
                getInstance().sendConnectMessageToServer();
            });
            ClientPlayerEvent.CLIENT_PLAYER_QUIT.register((player) -> {
                getInstance().setDisconnected();
            });
        }
    }

    public void setDisconnected() {
        if (ClientConfigDefinition.enable.get()) {
            MusicHud.setStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
        }
    }

    public void sendConnectMessageToServer() {
        if (ClientConfigDefinition.enable.get()) {
            NetworkManager.sendToServer(new ConnectRequest(Version.current));
        }
    }

    public void loginToServer() {
        if (isLogined()) {
            logger.info("Previous cookie found");
            LoginCookieInfo.refreshIfNecessaryAndRegisterToServer();
        } else {
            logger.info("No previous cookie found, login as anonymous");
            loginAsAnonymousToServer();
        }
    }

    public void loginAsAnonymousToServer() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        if (loginCookieInfo.type() == LoginType.ANONYMOUS) {
            NetworkManager.sendToServer(new CookieLoginRequest(loginCookieInfo, false));
        } else {
            NetworkManager.sendToServer(AnonymousLoginRequest.REQUEST);
        }
    }
}