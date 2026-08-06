package indi.etern.musichud.connection;

import indi.etern.musichud.Version;

/**
 * Result of the last connection attempt, kept by {@link ConnectionStateMachine}.
 *
 * @param outcome       how the attempt ended
 * @param serverVersion server version observed during the attempt, may be {@code null}
 */
public record ConnectAttemptResult(ConnectOutcome outcome, Version serverVersion) {
}
