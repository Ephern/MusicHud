package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Byte-level ledger of a playback task: the single source of truth for decoded
 * bytes, fed bytes, queued bytes, prefetched bytes and the actual playback
 * position, plus the queue of PCM chunks fed to OpenAL but not fully played yet.
 * <p>
 * No OpenAL dependency. Write access is domain-partitioned:
 * {@link #decodedBytes} and {@link #prefetchBytes} are written by the download
 * worker, the rest by the play worker. External readers use {@link #snapshot()}.
 * <p>
 * Synchronization/re-streaming events are explicit: sync skips only advance
 * {@link #decodedBytes} (skipped content never enters the ledger queue),
 * format changes call {@link #resetQueue()} (the sample offset is only valid
 * within a single format segment), full restarts call {@link #resetAll()}.
 */
public final class PlaybackLedger {
    public record LedgerEntry(ByteBuffer pcm, int format, int sampleRate, int bytes, int sampleCount) {
    }

    public final AtomicLong decodedBytes = new AtomicLong(0);
    public final long totalBytes = -1;

    public final AtomicLong fedBytes = new AtomicLong(0);
    public final AtomicLong queuedBytes = new AtomicLong(0);
    public volatile long playbackBytes = 0;
    public final AtomicLong prefetchBytes = new AtomicLong(0);

    private final ArrayDeque<LedgerEntry> queued = new ArrayDeque<>();

    public void add(LedgerEntry entry) {
        queued.addLast(entry);
        queuedBytes.addAndGet(entry.bytes());
    }

    public void removeHead() {
        LedgerEntry entry = queued.pollFirst();
        if (entry != null) {
            queuedBytes.addAndGet(-entry.bytes());
        }
    }

    public LedgerEntry peekFirst() {
        return queued.peekFirst();
    }

    public void resetQueue() {
        queued.clear();
        queuedBytes.set(0);
        fedBytes.set(0);
        playbackBytes = 0;
    }

    public void resetAll() {
        resetQueue();
        decodedBytes.set(0);
        prefetchBytes.set(0);
    }

    public void anchor(long fedBytes) {
        this.fedBytes.set(fedBytes);
    }

    public LedgerSnapshot snapshot() {
        return new LedgerSnapshot(decodedBytes.get(), totalBytes, fedBytes.get(), queuedBytes.get(), playbackBytes, prefetchBytes.get());
    }
}
