package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Byte-level ledger of a playback task: decoded, fed, queued and prefetched bytes plus the queue of
 * PCM chunks fed to OpenAL. No OpenAL dependency.
 * <p>
 * {@link #decodedBytes} and {@link #prefetchBytes} are written by the download worker, the rest by
 * the play worker; readers use {@link #snapshot()}. Sync skips only advance {@link #decodedBytes},
 * format changes call {@link #resetQueue()}, restarts call {@link #resetAll()}.
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

    /** Bounded read-only window at the current OpenAL queue offset; called only by its play worker. */
    public ByteBuffer pcmWindow(long sampleOffset, int maxFrames) {
        LedgerEntry head = queued.peekFirst();
        if (head == null || sampleOffset < 0 || maxFrames < 1 || head.sampleCount() <= 0) {
            return ByteBuffer.allocate(0);
        }
        int frameBytes = head.bytes() / head.sampleCount();
        if (frameBytes < 1 || frameBytes > 32 || maxFrames > 16384) {
            return ByteBuffer.allocate(0);
        }
        ByteBuffer output = ByteBuffer.allocate(maxFrames * frameBytes);
        long skip = sampleOffset > Long.MAX_VALUE / frameBytes ? Long.MAX_VALUE : sampleOffset * frameBytes;
        for (LedgerEntry entry : queued) {
            if (entry.format() != head.format() || entry.sampleRate() != head.sampleRate()) break;
            ByteBuffer pcm = entry.pcm().duplicate();
            int available = pcm.remaining() - pcm.remaining() % frameBytes;
            if (skip >= available) { skip -= available; continue; }
            pcm.position(pcm.position() + (int) skip);
            int count = Math.min(available - (int) skip, output.remaining());
            pcm.limit(pcm.position() + count);
            output.put(pcm);
            skip = 0;
            if (!output.hasRemaining()) break;
        }
        return output.flip().asReadOnlyBuffer();
    }

    /**
     * Detach and return every entry currently queued in the OpenAL source (in
     * playback order), clearing the queue and its byte counter. Used when the
     * context is lost: the OpenAL buffers are gone, but the PCM they held is
     * still referenced here and can be replayed by the next source.
     */
    public List<LedgerEntry> drainQueue() {
        List<LedgerEntry> drained = List.copyOf(queued);
        queued.clear();
        queuedBytes.set(0);
        return drained;
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
