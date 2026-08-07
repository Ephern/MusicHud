package indi.etern.musichud.connection;

/**
 * Outcome of the last connection attempt. This is a <em>result</em>, not a state:
 * the connection state machine only tracks {@link ConnectionStateMachine.ConnectionState},
 * while an incompatible/denied attempt is recorded here for the UI to query.
 */
public enum ConnectOutcome {
    /** Connection was denied, or client/server versions are incompatible. */
    INCOMPATIBLE,
    /** No ConnectResponse arrived in time (the server does not support the mod). */
    TIMEOUT
}
