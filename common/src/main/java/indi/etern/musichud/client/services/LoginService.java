package indi.etern.musichud.client.services;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.account.AccountView;
import indi.etern.musichud.client.ui.pages.account.LoginView;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.pushMessages.c2s.LogoutMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.AnonymousLoginRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.CookieLoginRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.StartQRLoginResponse;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LoginService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private final static ClientConfig clientConfig = ClientConfig.getInstance();
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    private static volatile LoginService instance = null;
    @Getter
    private final List<Consumer<LoginCookieInfo>> loginCompleteListeners = new ArrayList<>();
    @Getter
    NetworkReceiver<LoginResultMessage> loginResultReceiver = (loginResult, player) -> {
        MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Login-V");
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
                    MuiModApi.postToUiThread(() -> {
                        AccountView accountView = AccountView.getInstance();
                        if (accountView != null) {
                            accountView.refresh();
                        }
                    });
                } else {
                    MuiModApi.postToUiThread(() -> {
                        AccountView accountView = AccountView.getInstance();
                        if (accountView != null) {
                            accountView.refresh();
                        }
                        LoginView loginView = LoginView.getInstance();
                        if (loginView != null) {
                            loginView.reset();
                            String message = loginResult.message();
                            if (message.startsWith(MusicHud.MOD_ID)) {
                                message = I18n.get(message);
                            }
                            loginView.errorText(message);
                        }
                    });
                }
            }
        });
    };
    @Setter
    private Consumer<StartQRLoginResponse> loginResponseHandler;
    @Getter
    NetworkReceiver<StartQRLoginResponse> qrLoginResponseReceiver = (qrLoginResponse, player) -> {
        if (loginResponseHandler != null)
            loginResponseHandler.accept(qrLoginResponse);
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
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        Profile.setCurrent(Profile.ANONYMOUS);
    }

    public void setDisconnected() {
        MusicHud.setStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
        Profile.setCurrent(Profile.ANONYMOUS);
    }

    public void sendConnectMessageToServer() {
        if (clientConfig.getEnable()) {
            MusicHud.EXECUTOR.submit(() -> {
                clientNetworkService.sendToServer(new ConnectRequest(Version.current));
            });
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
            clientNetworkService.sendToServer(new CookieLoginRequest(loginCookieInfo, false));
        } else {
            clientNetworkService.sendToServer(AnonymousLoginRequest.REQUEST);
        }
    }

    @RegisterMark
    public static final class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            IClientEventService eventService = IClientEventService.getInstance();
            eventService.registerClientPlayerJoin((player) -> {
                getInstance().sendConnectMessageToServer();
            });
            eventService.registerClientPlayerQuit((player) -> {
                getInstance().setDisconnected();
            });
        }
    }
}