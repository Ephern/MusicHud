package indi.etern.musichud.client.services;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.account.LoginView;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.pushMessages.c2s.AnonymousLoginMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.CookieLoginMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.LogoutMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginService implements IClientLoginService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    private static final Period refreshInterval = Period.of(0, 0, 1);
    private static volatile LoginService instance = null;
    private final List<Consumer<LoginState>> loginStateListeners = new CopyOnWriteArrayList<>();
    private volatile LoginState loginState = refreshLoginState();
    @Getter
    private volatile String lastLoginErrorMessage;
    @Getter
    NetworkReceiver<LoginResultMessage> loginResultReceiver = (loginResult, player) -> {
        MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Login-V");
            LoginCookieInfo loginCookieInfo = loginResult.loginCookieInfo();
            LoginType type = loginCookieInfo.type();
            Profile profile = loginResult.profile();
            if (type != LoginType.UNLOGGED && type != LoginType.ANONYMOUS && loginResult.success()) {
                loginCookieInfo.setToClientCookie();
                Profile.setCurrent(profile);
                lastLoginErrorMessage = null;
            } else if (type == LoginType.ANONYMOUS && Profile.ANONYMOUS.equals(profile)) {
                loginCookieInfo.setToClientCookie();
                Profile.setCurrent(Profile.ANONYMOUS);
                lastLoginErrorMessage = null;
            } else {
                logger.warn("Login failed");
                lastLoginErrorMessage = resolveLoginErrorMessage(loginResult.message());
            }
            notifyLoginStateChanged((this::refreshLoginState));
            if (loginResult.success()) {
                // Every successful login starts a new server session; re-sync the local idle
                // play sources so they are pushed to whichever server-side component is active
                // (network channel when connected, local loopback in isolated mode). This must
                // not depend on the login state listeners, which are deduplicated and may not
                // fire when the state is unchanged across a connection switch.
                IIdlePlaySourceLayerState localState = MusicService.getInstance().getIdlePlaySourceState().local();
                localState.reset();
                localState.loadFromConfig();
            }
            AccountBaseView accountBaseView = AccountBaseView.getInstance();
            if (accountBaseView != null) {
                if (loginResult.success()) {
                    ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
                    profileConfigData.setProfile(profile);
                    profileConfigData.saveToConfig();
                    MuiModApi.postToUiThread(accountBaseView::refresh);
                } else {
                    MuiModApi.postToUiThread(() -> {
                        accountBaseView.refresh();
                        String message = resolveLoginErrorMessage(loginResult.message());
                        accountBaseView.onLoginFailed(message);
                        LoginView loginView = LoginView.getInstance();
                        if (loginView != null) {
                            loginView.reset();
                            loginView.errorText(message);
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

    private static void loginToServerByCookieWithRefreshCheck() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        if (loginCookieInfo.generateTime().plus(refreshInterval).isBefore(ZonedDateTime.now())) {
            logger.info("Refreshing Login Cookie");
            IClientNetworkService.getInstance().sendToServer(new CookieLoginMessage(loginCookieInfo, true));
        } else {
            IClientNetworkService.getInstance().sendToServer(new CookieLoginMessage(loginCookieInfo, false));
        }
    }

    @Override
    public boolean isLogined() {
        return refreshLoginState() == LoginState.LOGGED_IN;
    }

    @Override
    public LoginState refreshLoginState() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        LoginType type = loginCookieInfo.type();
        Profile current = Profile.getCurrent();
        boolean realCookie = type != LoginType.UNLOGGED && type != LoginType.ANONYMOUS;
        boolean realProfile = current != null && !current.equals(Profile.ANONYMOUS);
        if (realCookie && realProfile) {
            return LoginState.LOGGED_IN;
        }
        if (type == LoginType.ANONYMOUS || Profile.ANONYMOUS.equals(current)) {
            return LoginState.ANONYMOUS;
        }
        return LoginState.UNLOGGED;
    }

    @Override
    public Unregister addLoginStateListener(Consumer<LoginState> listener) {
        loginStateListeners.add(listener);
        return () -> loginStateListeners.remove(listener);
    }

    private void notifyLoginStateChanged(Supplier<LoginState> supplier) {
        LoginState state = supplier.get();
        if (state == loginState) return;
        loginState = state;
        loginStateListeners.forEach(listener -> listener.accept(state));
    }

    private static String resolveLoginErrorMessage(String message) {
        if (message != null && message.startsWith(MusicHud.MOD_ID)) {
            return I18n.get(message);
        }
        return message;
    }

    public void clearLastLoginErrorMessage() {
        lastLoginErrorMessage = null;
    }

    @Override
    public boolean hasPreviousLoginInfo() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        return loginCookieInfo.type() != LoginType.UNLOGGED &&
                loginCookieInfo.type() != LoginType.ANONYMOUS;
    }

    @Override
    public void loginToServer() {
        if (hasPreviousLoginInfo()) {
            logger.info("Previous cookie found");
            loginToServerByCookieWithRefreshCheck();
        } else {
            logger.info("No previous cookie found, login as anonymous");
            loginAsAnonymousToServer();
        }
    }

    private void loginAsAnonymousToServer() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        if (loginCookieInfo.type() == LoginType.ANONYMOUS) {
            clientNetworkService.sendToServer(new CookieLoginMessage(loginCookieInfo, false));
        } else {
            clientNetworkService.sendToServer(AnonymousLoginMessage.REQUEST);
        }
    }

    @Override
    public void logoutAndReloginAsAnonymous() {
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        Profile.setCurrent(Profile.ANONYMOUS);
        notifyLoginStateChanged(this::refreshLoginState);
        loginAsAnonymousToServer();
    }

    @RegisterMark
    public static final class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            IClientEventService eventService = IClientEventService.getInstance();
            eventService.registerClientPlayerJoin((player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    ServerData currentServer = Minecraft.getInstance().getCurrentServer();
                    if (currentServer != null) {
                        boolean autoConnectToServer = clientConfig.getEnableAutoConnect();
                        if (autoConnectToServer) {
                            AutoConnectServerFilterType connectServerFilterType = clientConfig.getConnectServerFilterType();
                            if ((connectServerFilterType == AutoConnectServerFilterType.BLACK_LIST
                                    && clientConfig.getBlackList().stream().noneMatch(i -> Pattern.matches(i, currentServer.ip)))
                                    || (connectServerFilterType == AutoConnectServerFilterType.WHITE_LIST
                                    && clientConfig.getWhiteList().stream().anyMatch(i -> Pattern.matches(i, currentServer.ip)))) {
                                IConnectionManager.getInstance().connectToExternalServer();
                            } else {
                                IConnectionManager.getInstance().launchIsolated();
                            }
                        } else {
                            IConnectionManager.getInstance().launchIsolated();
                        }
                    } else {
                        // Single Player: try external first, fall back to isolated on timeout
                        IConnectionManager.getInstance().connectToExternalServer();
                    }
                });
            });
            eventService.registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    IConnectionManager.getInstance().onPlayerQuit();
                    if (ConnectionStateMachine.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                        if (clientConfig.getEnableIsolatedMode()) {
                            LoginApiService.getInstance().logout(VanillaPlayerProxy.ofPlayer(player));
                        }
                    } else {
                        ConnectionStateMachine.enterDisconnected();
                    }
                    LoginService loginService = LoginService.getInstance();
                    loginService.notifyLoginStateChanged(() -> LoginState.UNLOGGED);
                });
            });
        }
    }
}
