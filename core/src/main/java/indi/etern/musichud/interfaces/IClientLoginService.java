package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.platform.Environment;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IClientLoginService {
    static IClientLoginService getInstance() {
        Environment currentEnvironment = MusicHud.getCurrentEnvironment();
        if (currentEnvironment.getSide() == Environment.Side.CLIENT) {
            Environment.Platform platform = currentEnvironment.getPlatform();
            Supplier<IClientLoginService> supplier = platform.getClientLoginServiceSupplier();
            if (supplier != null) {
                IClientLoginService iClientLoginService = supplier.get();
                if (iClientLoginService != null) {
                    return iClientLoginService;
                }
            }
        }
        throw new UnsupportedOperationException();
    }

    enum ConnectionType {
        EXTERNAL, INTERNAL
    }

    enum LoginState {
        UNLOGGED,
        ANONYMOUS,
        LOGGED_IN
    }

    boolean isLogined();

    LoginState getLoginState();

    Unregister addLoginStateListener(Consumer<LoginState> listener);

    boolean hasPreviousLoginInfo();

    void connectAsPrevious();

    void loginToServer(ConnectionType type);

    void logoutAndReloginAsAnonymous();

    void disconnectToExternalOrIntegratedServer();

    void switchToIsolate();

    void switchToServer();

    Boolean toggleConnection();

    void keyBindsToggleConnection();

    ConnectionType getConnectionType();

    NetworkReceiver<LoginResultMessage> getLoginResultReceiver();
}
