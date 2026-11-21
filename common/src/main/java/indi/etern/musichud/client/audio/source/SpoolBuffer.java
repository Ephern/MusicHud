package indi.etern.musichud.client.audio.source;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.utils.TempService;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only disk buffer between the network producer and the decoder: the
 * stream offset equals the file size, so a truncated transfer resumes with a
 * plain {@code Range} request. Writers block once {@code capacity} bytes ahead
 * of the reader.
 */
public final class SpoolBuffer implements AutoCloseable {
    /** Supplies a fresh signed URL when the current one stops working. */
    @FunctionalInterface
    public interface UrlRefresher {
        @Nullable String refresh(String staleUrl);
    }

    private static final Logger LOGGER = MusicHud.getLogger(SpoolBuffer.class);
    private static final long MIN_CAPACITY = 16L * 1024 * 1024;
    private static final long MAX_CAPACITY = 256L * 1024 * 1024;
    private static final long DEFAULT_CAPACITY = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_UNKNOWN = 512L * 1024 * 1024;
    private static final long TOTAL_SLACK = 1024 * 1024;
    private static final long MIN_FREE_SPACE = 256L * 1024 * 1024;
    /** Budget for all live spools combined. */
    private static final long MAX_GLOBAL_BYTES = 512L * 1024 * 1024;
    private static final AtomicLong reservedBytes = new AtomicLong();

    private final Path file;
    private final FileChannel channel;
    private final long capacity;
    private final long maxTotal;
    private final long reservation;
    private final SpoolProducer producer;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private long written;
    private long readPosition;
    private long expected;
    private boolean complete;
    private boolean closed;
    private Throwable failure;

    private SpoolBuffer(Path file, FileChannel channel, long expected, long reservation) {
        this.file = file;
        this.channel = channel;
        this.reservation = reservation;
        this.expected = Math.max(0, expected);
        this.capacity = Math.clamp(this.expected > 0 ? this.expected : DEFAULT_CAPACITY, MIN_CAPACITY, MAX_CAPACITY);
        this.maxTotal = this.expected > 0 ? this.expected + TOTAL_SLACK : MAX_TOTAL_UNKNOWN;
        this.producer = new SpoolProducer(this);
    }

    /**
     * Opens the spool file and starts the producer.
     *
     * @return {@code null} when temp storage is unavailable
     */
    public static @Nullable SpoolBuffer open(String url, long expectedSize,
                                             @Nullable UrlRefresher refresher,
                                             String namePrefix) {
        TempService.Session session = TempService.shared();
        if (session == null) {
            LOGGER.debug("Audio spool unavailable: no temp session");
            return null;
        }
        long usable = session.usableSpace();
        if (usable > 0 && usable < MIN_FREE_SPACE) {
            LOGGER.warn("Audio spool unavailable: only {} MiB free in temp storage", usable / (1024 * 1024));
            return null;
        }
        long reservation = expectedSize > 0 ? Math.min(expectedSize, MAX_CAPACITY) : DEFAULT_CAPACITY;
        if (!reserve(reservation)) {
            LOGGER.warn("Audio spool unavailable: {} MiB global budget exhausted", MAX_GLOBAL_BYTES / (1024 * 1024));
            return null;
        }
        Path file = null;
        FileChannel channel = null;
        try {
            file = session.allocateAudio(namePrefix, ".part");
            channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.READ);
            SpoolBuffer buffer = new SpoolBuffer(file, channel, expectedSize, reservation);
            buffer.producer.start(url, refresher);
            LOGGER.debug("Audio spool opened: expected={} MiB capacityMiB={} file={}",
                    expectedSize > 0 ? String.format("%.1f", expectedSize / 1048576.0) : "?",
                    buffer.capacity / (1024 * 1024), file.getFileName());
            return buffer;
        } catch (Exception e) {
            LOGGER.warn("Failed to open audio spool, falling back to direct streaming", e);
            reservedBytes.addAndGet(-reservation);
            closeQuietly(channel, file);
            return null;
        }
    }

    private static boolean reserve(long bytes) {
        while (true) {
            long current = reservedBytes.get();
            if (current + bytes > MAX_GLOBAL_BYTES) return false;
            if (reservedBytes.compareAndSet(current, current + bytes)) return true;
        }
    }

    /** Whether the next {@code length} bytes would exceed this spool's hard size bound. */
    boolean rejects(int length) {
        lock.lock();
        try {
            return written + length > maxTotal;
        } finally {
            lock.unlock();
        }
    }

    private static void closeQuietly(FileChannel channel, Path file) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
        if (file != null) {
            try {
                java.nio.file.Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }
    }

    /** Reader view starting at byte 0. */
    public InputStream openStream() {
        lock.lock();
        try {
            readPosition = 0;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        return new Reader();
    }

    public boolean isUsable() {
        lock.lock();
        try {
            return !closed && failure == null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isComplete() {
        lock.lock();
        try {
            return complete;
        } finally {
            lock.unlock();
        }
    }

    public long expected() {
        lock.lock();
        try {
            return expected;
        } finally {
            lock.unlock();
        }
    }

    public long written() {
        lock.lock();
        try {
            return written;
        } finally {
            lock.unlock();
        }
    }

    /** Server-reported size replaces the API metadata. */
    void updateExpected(long bytes) {
        if (bytes <= 0) return;
        lock.lock();
        try {
            expected = bytes;
        } finally {
            lock.unlock();
        }
    }

    /** Appends one block; blocks while the reader is {@code capacity} bytes behind. */
    void append(byte[] data, int length) throws IOException, InterruptedException {
        lock.lockInterruptibly();
        try {
            while (!closed && written - readPosition >= capacity) changed.await();
            if (closed) throw new IOException("Audio spool closed");
            if (written + length > maxTotal) {
                throw new IOException("Audio stream exceeded the expected size (" + written + " + " + length + " > " + maxTotal + ")");
            }
            long base = written;
            int done = 0;
            while (done < length) {
                int n = channel.write(ByteBuffer.wrap(data, done, length - done), base + done);
                if (n <= 0) throw new IOException("Audio spool write stalled at " + (base + done));
                done += n;
            }
            written += done;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void markComplete() {
        lock.lock();
        try {
            complete = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void markFailed(Throwable cause) {
        lock.lock();
        try {
            if (failure == null) failure = cause;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        reservedBytes.addAndGet(-reservation);
        producer.cancel();
        try {
            channel.close();
        } catch (IOException ignored) {
        }
        try {
            java.nio.file.Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete audio spool file {}", file, e);
        }
    }

    private final class Reader extends InputStream {
        private long position;
        private boolean readerClosed;

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read <= 0 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (readerClosed) throw new IOException("Audio spool reader closed");
            lock.lock();
            try {
                while (!readerClosed && !closed && !complete && failure == null && position >= written) {
                    changed.await();
                }
                if (readerClosed) throw new IOException("Audio spool reader closed");
                if (closed) throw new IOException("Audio spool closed");
                if (position >= written) {
                    if (failure != null) throw new IOException("Audio stream interrupted at " + position + " bytes", failure);
                    return -1;
                }
                int toRead = (int) Math.min(len, written - position);
                int read = channel.read(ByteBuffer.wrap(b, off, toRead), position);
                if (read > 0) {
                    position += read;
                    readPosition = position;
                    changed.signalAll();
                }
                return read;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted = new InterruptedIOException("Audio spool read interrupted");
                interrupted.bytesTransferred = 0;
                throw interrupted;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int available() {
            lock.lock();
            try {
                return Math.clamp(written - position, 0, Integer.MAX_VALUE);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            lock.lock();
            try {
                readerClosed = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
