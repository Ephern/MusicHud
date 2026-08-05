package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import indi.etern.musichud.platform.Environment;

import java.util.function.Supplier;

/**
 * Centralized control of connection mode switching (external server vs isolated client).
 * Previously scattered across LoginService / MusicService / ConnectResponse handling,
 * which caused several bugs.
 */
public interface IConnectionManager {
    static IConnectionManager getInstance() {
        Environment currentEnvironment = MusicHud.getCurrentEnvironment();
        if (currentEnvironment.getSide() == Environment.Side.CLIENT) {
            Environment.Platform platform = currentEnvironment.getPlatform();
            Supplier<IConnectionManager> supplier = platform.getConnectionManagerSupplier();
            if (supplier != null) {
                IConnectionManager connectionManager = supplier.get();
                if (connectionManager != null) {
                    return connectionManager;
                }
            }
        }
        throw new UnsupportedOperationException();
    }

    enum ConnectionMode {
        DISCONNECTED, EXTERNAL, ISOLATED
    }

    ConnectionMode getMode();

    void connectToExternalServer();

    void launchIsolated();

    void switchToIsolate();

    void disconnect();

    /**
     * Handles a ConnectResponse from the server: drives the connection state machine.
     */
    void onConnectResponse(ConnectResponse response);
}
