package indi.etern.musichud.client.services;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.account.AccountView;
import indi.etern.musichud.client.ui.pages.account.LoginView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginService implements IClientLoginService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    private static final Period refreshInterval = Period.of(0, 0, 1);
    private static volatile LoginService instance = null;
    @Getter
    private final List<Consumer<LoginCookieInfo>> loginCompleteListeners = new ArrayList<>();
    private double lastPressTime;
    private static final long TOGGLE_DEBOUNCE_DELAY_MILLIS = 300;
    private final AtomicInteger toggleVersion = new AtomicInteger(0);
    @Getter
    private ConnectionType connectionType;
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
                            accountView.refresh(false);
                        }
                    });
                } else {
                    MuiModApi.postToUiThread(() -> {
                        AccountView accountView = AccountView.getInstance();
                        if (accountView != null) {
                            accountView.refresh(false);
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
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        return loginCookieInfo.type() != LoginType.UNLOGGED &&
                loginCookieInfo.type() != LoginType.ANONYMOUS;
    }

    @Override
    public void connectAsPrevious() {
        if (connectionType == ConnectionType.EXTERNAL) {
            IConnectionManager.getInstance().connectToExternalServer();
        } else {
            IConnectionManager.getInstance().launchIsolated();
        }
    }

    @Override
    public void loginToServer(ConnectionType type) {
        if (type != null) {
            connectionType = type;
        }
        if (isLogined()) {
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
    public void logout() {
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        Profile.setCurrent(Profile.ANONYMOUS);
        loginAsAnonymousToServer();
    }

    @Override
    public void disconnectToExternalOrIntegratedServer() {
        IConnectionManager.getInstance().disconnect();
    }

    @Override
    public void switchToIsolate() {
        IConnectionManager.getInstance().switchToIsolate();
    }

    @Override
    public void switchToServer() {
        IConnectionManager.getInstance().connectToExternalServer();
    }

    @Override
    public Boolean toggleConnection() {
        MusicHud.ConnectStatus connectStatus = MusicHud.getConnectStatus();
        if (connectStatus == MusicHud.ConnectStatus.CONNECTED) {
            final int version = toggleVersion.incrementAndGet();
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(TOGGLE_DEBOUNCE_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (toggleVersion.get() != version) return;
                if (clientConfig.getEnableIsolatedMode()) {
                    switchToIsolate();
                } else {
                    disconnectToExternalOrIntegratedServer();
                }
            });
            return true;
        } else if (connectStatus == MusicHud.ConnectStatus.NOT_CONNECTED) {
            final int version = toggleVersion.incrementAndGet();
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(TOGGLE_DEBOUNCE_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (toggleVersion.get() != version) return;
                switchToServer();
            });
            return false;
        } else {
            return null;
        }
    }

    @Override
    public void keyBindsToggleConnection() {
        boolean integratedServer = Minecraft.getInstance().getCurrentServer() == null;
        if (!integratedServer) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastPressTime <= 3000) {
                lastPressTime = 0;
                Boolean connected = toggleConnection();
                if (connected != null) {
                    MuiModApi.postToUiThread(() -> {
                        //noinspection UnstableApiUsage
                        Context context = UIManager.getInstance().getDecorView().getContext();
                        if (connected) {
                            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.disconnecting"), Toast.LENGTH_SHORT));
                        } else {
                            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.connecting"), Toast.LENGTH_SHORT));
                        }
                    });
                }
            } else {
                lastPressTime = currentTimeMillis;
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.confirmSwitchConnection"), Toast.LENGTH_SHORT));
                });
            }
        } else {
            MuiModApi.postToUiThread(() -> {
                //noinspection UnstableApiUsage
                Context context = UIManager.getInstance().getDecorView().getContext();
                ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.switchConnectionUnavailableInIntegratedServer"), Toast.LENGTH_SHORT));
            });
        }
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
                    if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                        if (clientConfig.getEnableIsolatedMode()) {
                            LoginApiService.getInstance().logout(VanillaPlayerProxy.ofPlayer(player));
                        }
                    } else {
                        MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
                    }
                });
            });
        }
    }
}
