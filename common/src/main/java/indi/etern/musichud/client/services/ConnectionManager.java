package indi.etern.musichud.client.services;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.connection.ConnectAttemptResult;
import indi.etern.musichud.connection.ConnectOutcome;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.connection.ConnectionStateMachine.ConnectionState;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.IConnectionManager;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.LogoutMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ConfirmConnectMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetInitialStateRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetInitialStateResponse;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized connection mode control.
 * Previously the connected/isolated switching logic was scattered across LoginService,
 * MusicService and the ConnectResponse receiver, which repeatedly caused bugs.
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConnectionManager implements IConnectionManager {
    private static final Logger logger = MusicHud.getLogger(ConnectionManager.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final long TOGGLE_DEBOUNCE_DELAY_MILLIS = 300;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static volatile ConnectionManager instance;
    private final AtomicInteger toggleVersion = new AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger connectGeneration = new java.util.concurrent.atomic.AtomicInteger(0);
    private double lastPressTime;
    private volatile LastMode lastMode = LastMode.NONE;
    @Getter
    private Version serverVersion;

    private enum LastMode {
        NONE, EXTERNAL, ISOLATED
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) {
                    instance = new ConnectionManager();
                }
            }
        }
        return instance;
    }

    @Override
    public synchronized void connectToExternalServer() {
        if (clientConfig.getEnable() && ConnectionStateMachine.enterConnecting()) {
            lastMode = LastMode.EXTERNAL;
            clientNetworkService.sendToServer(new ConnectRequest(Version.current));
            scheduleConnectTimeoutFallback();
        }
    }

    private void scheduleConnectTimeoutFallback() {
        int generation = connectGeneration.incrementAndGet();
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(CONNECT_TIMEOUT.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (ConnectionManager.this) {
                if (generation == connectGeneration.get()
                        && ConnectionStateMachine.getState() == ConnectionState.CONNECTING) {
                    logger.warn("No ConnectResponse within {}, falling back to isolated mode", CONNECT_TIMEOUT);
                    launchIsolated();
                    ConnectionStateMachine.recordResult(new ConnectAttemptResult(ConnectOutcome.TIMEOUT, null));
                }
            }
        });
    }

    @Override
    public synchronized void launchIsolated() {
        if (!clientConfig.getEnable() || !ConnectionStateMachine.enterIsolated()) {
            return;
        }
        lastMode = LastMode.ISOLATED;
        IClientLoginService.getInstance().loginToServer();
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();
        requestInitialState();
    }

    @Override
    public synchronized void switchToIsolate() {
        disconnect();
        launchIsolated();
    }

    @Override
    public synchronized void disconnect() {
        ConnectionStateMachine.enterDisconnected();
        clientNetworkService.sendToServer(LogoutMessage.MESSAGE);
        MusicService.resetCurrentMusicStatus();
        NowPlayingInfo.getInstance().stop();
        StreamAudioPlayer.getInstance().stop();
        connectGeneration.incrementAndGet();
    }

    @Override
    public Boolean toggleConnection() {
        ConnectionState state = ConnectionStateMachine.getState();
        if (state == ConnectionState.CONNECTED) {
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
                    disconnect();
                }
            });
            return true;
        } else {
            final int version = toggleVersion.incrementAndGet();
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(TOGGLE_DEBOUNCE_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (toggleVersion.get() != version) return;
                connectToExternalServer();
            });
            return false;
        }
    }

    @Override
    public void onPlayerQuit() {
        // Invalidate any pending debounced toggle task so it cannot act after the player
        // has left the server/world (e.g. start isolated mode from the main menu).
        toggleVersion.incrementAndGet();
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

    @Override
    public void connectAsPrevious() {
        // Restore the last connection mode instead of guessing from the current state
        // (e.g. after re-enabling the mod, the state is always DISCONNECTED).
        if (lastMode == LastMode.EXTERNAL) {
            IConnectionManager.getInstance().connectToExternalServer();
        } else {
            IConnectionManager.getInstance().launchIsolated();
        }
    }

    @Override
    public synchronized void onConnectResponse(ConnectResponse payload) {
        this.serverVersion = payload.serverVersion();
        IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
        IClientLoginService clientLoginService = IClientLoginService.getInstance();
        if (ConnectionStateMachine.getState() == ConnectionState.CONNECTING
                && payload.accepted() && Version.compatibleWith(payload.serverVersion())) {
            logger.info("Connecting accepted, server version: {}", payload.serverVersion());
            if (!clientDistUtil.inIntegratedServer()
                    && clientConfig.getEnableIsolatedMode()) {
                // Stop leftover isolated-mode playback before switching to server tracks.
                MusicService.resetCurrentMusicStatus();
                NowPlayingInfo.getInstance().stop();
                StreamAudioPlayer.getInstance().stop();
            }
            connectGeneration.incrementAndGet();
            clientNetworkService.sendToServer(ConfirmConnectMessage.MESSAGE);
            ConnectionStateMachine.enterConnected();
            clientLoginService.loginToServer();
            requestInitialState();
        } else if (ConnectionStateMachine.getState() == ConnectionState.CONNECTING) {
            // Only treat the response as a rejection while actually waiting for it; a stale
            // response arriving in any other state must not yank the player out of isolated mode.
            logger.info("Connecting denied, client version ({}) is compatible with server version ({})", Version.current, payload.serverVersion());
            switchToIsolate();
            ConnectionStateMachine.recordResult(new ConnectAttemptResult(ConnectOutcome.INCOMPATIBLE, payload.serverVersion()));
        } else if (ConnectionStateMachine.getState() == ConnectionState.CONNECTED && !payload.accepted()) {
            // The server actively kicked the player (e.g. disconnectToAll when the
            // integrated-server features are turned off): fall back to isolated mode.
            logger.info("Disconnected by server, falling back to isolated mode");
            switchToIsolate();
        }
        clientDistUtil.refreshMainGUI();
    }

    private void requestInitialState() {
        ConnectionState requestedState = ConnectionStateMachine.getState();
        MusicHud.EXECUTOR.execute(() -> {
            RequestResponseManager.send(
                            new GetInitialStateRequest(),
                            GetInitialStateResponse.class,
                            Duration.ofSeconds(5))
                    .thenAccept(response -> {
                        synchronized (ConnectionManager.this) {
                            if (ConnectionStateMachine.getState() != requestedState) {
                                logger.debug("Initial state response ignored, state changed from {} to {}", requestedState, ConnectionStateMachine.getState());
                                return;
                            }
                        }
                        MusicService.getInstance().refreshQueue(response.getQueue());
                        if (response.getCurrentPlaying() != MusicDetail.NONE) {
                            MusicService.getInstance().switchMusic(
                                    response.getCurrentPlaying(), response.getNextIdle(), response.getStartTime(), "");
                        } else {
                            MusicService.getInstance().switchMusic(
                                    MusicDetail.NONE, response.getNextIdle(), response.getStartTime(), "");
                        }
                        MusicService.getInstance().getIdlePlaySourceState().external().updateAll(
                                response.getPlaylistSources(), response.getAlbumSources());
                    })
                    .exceptionally(e -> {
                        logger.warn("Failed to get initial state", e);
                        return null;
                    });
        });
    }
}
