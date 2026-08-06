package indi.etern.musichud.connection;

import indi.etern.musichud.MusicHud;

import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Map;

/**
 * Single source of truth for the client's connection state.
 *
 * <p>The fine-grained {@link ConnectionState} drives behavior (distinguishes "waiting for a
 * ConnectResponse" from "isolated mode" even though both surface as {@code NOT_CONNECTED}).
 * How the last attempt ended (denied/incompatible, timeout) is NOT a state: it is recorded
 * as a {@link ConnectAttemptResult} and surfaced to the UI through the derived coarse
 * {@link MusicHud.ConnectStatus}.
 *
 * <p>Transitions are guarded: invalid ones are rejected and reported via the boolean return
 * value so the caller can skip its side effects.
 */
public final class ConnectionStateMachine {
    private static final Map<ConnectionState, EnumSet<ConnectionState>> ALLOWED = new EnumMap<>(ConnectionState.class);

    static {
        ALLOWED.put(ConnectionState.DISCONNECTED, EnumSet.of(
                ConnectionState.DISCONNECTED, ConnectionState.CONNECTING,
                ConnectionState.CONNECTED, ConnectionState.ISOLATED));
        ALLOWED.put(ConnectionState.CONNECTING, EnumSet.of(
                ConnectionState.CONNECTING, ConnectionState.CONNECTED,
                ConnectionState.ISOLATED, ConnectionState.DISCONNECTED));
        ALLOWED.put(ConnectionState.CONNECTED, EnumSet.of(
                ConnectionState.CONNECTED, ConnectionState.DISCONNECTED, ConnectionState.CONNECTING));
        ALLOWED.put(ConnectionState.ISOLATED, EnumSet.of(
                ConnectionState.ISOLATED, ConnectionState.CONNECTING, ConnectionState.DISCONNECTED));
    }

    private static volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private static volatile ConnectAttemptResult lastConnectResult;

    private ConnectionStateMachine() {
    }

    public enum ConnectionState {
        /** No server session; idle. */
        DISCONNECTED,
        /** ConnectRequest sent, waiting for the ConnectResponse (external server). */
        CONNECTING,
        /** Connected to the external server. */
        CONNECTED,
        /** Running without a server, in isolated mode. */
        ISOLATED
    }

    public static ConnectionState getState() {
        return state;
    }

    /** Result of the last connection attempt, {@code null} if none is relevant. */
    public static ConnectAttemptResult getLastConnectResult() {
        return lastConnectResult;
    }

    /**
     * Records the outcome of the last connection attempt. Only kept while in
     * {@link ConnectionState#ISOLATED}; re-publishes the coarse status since the state
     * itself may be unchanged but the derived status may change (e.g. NOT_CONNECTED → INCOMPATIBLE).
     */
    public static synchronized void recordResult(ConnectAttemptResult result) {
        lastConnectResult = result;
        MusicHud.setConnectStatus(getConnectStatus());
    }

    /** Coarse status exposed to the UI. */
    public static MusicHud.ConnectStatus getConnectStatus() {
        if (state == ConnectionState.CONNECTED) {
            return MusicHud.ConnectStatus.CONNECTED;
        }
        if (state != ConnectionState.CONNECTING
                && lastConnectResult != null
                && lastConnectResult.outcome() == ConnectOutcome.INCOMPATIBLE) {
            return MusicHud.ConnectStatus.INCOMPATIBLE;
        }
        return MusicHud.ConnectStatus.NOT_CONNECTED;
    }

    /** @return {@code true} if the transition was accepted, {@code false} if it is not allowed. */
    private static synchronized boolean transition(ConnectionState target) {
        if (!ALLOWED.get(state).contains(target)) {
            return false;
        }
        // The recorded attempt result is only meaningful while running in isolated mode;
        // any other transition invalidates it.
        if (target != ConnectionState.ISOLATED) {
            lastConnectResult = null;
        }
        if (state != target) {
            state = target;
            MusicHud.setConnectStatus(getConnectStatus());
        }
        return true;
    }

    /** Initiates an external-server connection attempt. */
    public static boolean enterConnecting() {
        return transition(ConnectionState.CONNECTING);
    }

    /** The server accepted the connection. */
    public static boolean enterConnected() {
        return transition(ConnectionState.CONNECTED);
    }

    /** Enters isolated (server-less) mode. */
    public static boolean enterIsolated() {
        return transition(ConnectionState.ISOLATED);
    }

    /** Tear down the current session. */
    public static boolean enterDisconnected() {
        return transition(ConnectionState.DISCONNECTED);
    }
}
