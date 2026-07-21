package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.login.LoginCookieInfo;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.StartQRLoginResponse;
import indi.etern.musichud.platform.Environment;

import java.util.List;
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

    boolean isLogined();

    void connectAsPrevious();

    void connectToExternalServer();

    void loginToServer(ConnectionType type);

    void logout();

    void disconnectToExternalOrIntegratedServer();

    void switchToIsolate();

    void switchToServer();

    Boolean toggleConnection();

    void keyBindsToggleConnection();

    List<Consumer<LoginCookieInfo>> getLoginCompleteListeners();

    NetworkReceiver<StartQRLoginResponse> getQrLoginResponseReceiver();

    ConnectionType getConnectionType();

    NetworkReceiver<LoginResultMessage> getLoginResultReceiver();

    void setLoginResponseHandler(Consumer<StartQRLoginResponse> loginResponseHandler);
}
