package indi.etern.musichud.client.services;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.ToastUtil;
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
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LoginService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final Logger logger = MusicHud.getLogger(LoginService.class);
    private static final Period refreshInterval = Period.of(0, 0, 1);
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
    private double lastPressTime;

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
            IClientNetworkService.getInstance().sendToServer(new CookieLoginRequest(loginCookieInfo, true));
        } else {
            IClientNetworkService.getInstance().sendToServer(new CookieLoginRequest(loginCookieInfo, false));
        }
    }

    public boolean isLogined() {
        LoginCookieInfo loginCookieInfo = LoginCookieInfo.clientCurrentCookie();
        return loginCookieInfo.type() != LoginType.UNLOGGED &&
                loginCookieInfo.type() != LoginType.ANONYMOUS;
    }

    public void connectToExternalServer() {
        if (clientConfig.getEnable()) {
            clientNetworkService.sendToServer(new ConnectRequest(Version.current));
        }
    }

    public void loginToServer() {
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
            clientNetworkService.sendToServer(new CookieLoginRequest(loginCookieInfo, false));
        } else {
            clientNetworkService.sendToServer(AnonymousLoginRequest.REQUEST);
        }
    }

    public void logout() {
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        Profile.setCurrent(Profile.ANONYMOUS);
        loginAsAnonymousToServer();
    }

    public void disconnectToExternalOrIntegratedServer() {
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();

        MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
//        Profile.setCurrent(Profile.ANONYMOUS);
    }

    public void switchToIsolate() {
        disconnectToExternalOrIntegratedServer();
        launchIsolated();
    }

    private void launchIsolated() {
        loginToServer();
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();
        MusicPlayerServerService.getInstance().sendSyncPlayingStatusToPlayer(Minecraft.getInstance().player);
    }

    public void switchToServer() {
        connectToExternalServer();
    }

    public Boolean toggleConnection() {
        MusicHud.ConnectStatus connectStatus = MusicHud.getConnectStatus();
        if (connectStatus == MusicHud.ConnectStatus.CONNECTED) {
            if (clientConfig.getEnableIsolatedMode()) {
                switchToIsolate();
            } else {
                disconnectToExternalOrIntegratedServer();
            }
            return true;
        } else if (connectStatus == MusicHud.ConnectStatus.NOT_CONNECTED) {
            switchToServer();
            return false;
        } else {
            return null;
        }
    }

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
                ServerData currentServer = Minecraft.getInstance().getCurrentServer();
                if (currentServer != null) {
                    boolean autoConnectToServer = clientConfig.getEnableAutoConnect();
                    if (autoConnectToServer) {
                        AutoConnectServerFilterType connectServerFilterType = clientConfig.getConnectServerFilterType();
                        if ((connectServerFilterType == AutoConnectServerFilterType.BLACK_LIST
                                && clientConfig.getBlackList().stream().noneMatch(i -> Pattern.matches(i, currentServer.ip)))
                                || (connectServerFilterType == AutoConnectServerFilterType.WHITE_LIST
                                && clientConfig.getWhiteList().stream().anyMatch(i -> Pattern.matches(i, currentServer.ip)))) {
                            getInstance().connectToExternalServer();
                        } else {
                            getInstance().launchIsolated();
                        }
                    } else {
                        getInstance().launchIsolated();
                    }
                } else {
                    // Single Player
                    getInstance().connectToExternalServer();
                }
            });
            eventService.registerClientPlayerQuit((player) -> {
                if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                    if (clientConfig.getEnableIsolatedMode()) {
                        LoginApiService.getInstance().logout(player);
                    }
                } else {
                    MusicHud.setConnectStatus(MusicHud.ConnectStatus.NOT_CONNECTED);
                }
            });
        }
    }
}