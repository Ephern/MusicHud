package indi.etern.musichud.client.audio;

/**
 * Immutable byte-level state snapshot of a {@link PlaybackLedger}, exported
 * for external readers (progress queries, playback controls).
 */
public record LedgerSnapshot(
        long decodedBytes,
        long totalBytes,
        long fedBytes,
        long queuedBytes,
        long playbackBytes,
        long prefetchBytes) {
}
