package indi.etern.musichud.client.audio.source;

import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Future;

/**
 * Fills a {@link SpoolBuffer} from a remote object, resuming with {@code Range}
 * requests when a connection ends early.
 */
final class SpoolProducer {
    private static final Logger LOGGER = MusicHud.getLogger(SpoolProducer.class);
    private static final int BLOCK_SIZE = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int STATUS_RANGE_NOT_SATISFIABLE = 416;
    private static final long RETRY_BASE_MS = 1000;
    private static final long RETRY_MAX_MS = 15000;
    /** Consecutive attempts that deliver no new byte before the spool is declared dead. */
    private static final int MAX_IDLE_ATTEMPTS = 6;
    private static final int MAX_ATTEMPTS = 64;
    private static final int IDLE_ATTEMPTS_PER_URL_REFRESH = 2;

    private final SpoolBuffer buffer;
    private volatile boolean cancelled;
    private volatile Future<?> future;
    private volatile SpoolBuffer.UrlRefresher refresher;

    SpoolProducer(SpoolBuffer buffer) {
        this.buffer = buffer;
    }

    void start(String url, @Nullable SpoolBuffer.UrlRefresher refresher) {
        this.refresher = refresher;
        future = MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-AudioSpool");
            runFrom(url);
        });
    }

    void cancel() {
        cancelled = true;
        Future<?> running = future;
        if (running != null) running.cancel(true);
    }

    private void runFrom(@Nullable String initialUrl) {
        long startedAt = System.currentTimeMillis();
        String currentUrl = initialUrl;
        int attempts = 0;
        int idleAttempts = 0;
        while (!cancelled && buffer.isUsable() && attempts < MAX_ATTEMPTS) {
            long offset = buffer.written();
            long attemptStartedAt = System.currentTimeMillis();
            attempts++;
            boolean complete;
            try {
                complete = fetch(currentUrl, offset);
            } catch (FatalStreamException e) {
                if (cancelled || !buffer.isUsable()) return;
                LOGGER.warn("Audio spool gave up at {} bytes: {}", buffer.written(), e.getMessage());
                buffer.markFailed(e);
                return;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (cancelled || !buffer.isUsable()) return;
                boolean progressed = buffer.written() > offset;
                idleAttempts = progressed ? 0 : idleAttempts + 1;
                LOGGER.warn("Audio spool attempt {} failed after {} ms at offset {} (+{} bytes): {}: {}",
                        attempts, System.currentTimeMillis() - attemptStartedAt, offset,
                        buffer.written() - offset, e.getClass().getSimpleName(), e.getMessage());
                if (idleAttempts >= MAX_IDLE_ATTEMPTS) {
                    LOGGER.warn("Audio spool aborted: {} consecutive attempts made no progress", idleAttempts);
                    buffer.markFailed(e);
                    return;
                }
                if (!progressed && idleAttempts % IDLE_ATTEMPTS_PER_URL_REFRESH == 0) {
                    String refreshed = refreshUrl(currentUrl);
                    if (refreshed != null) currentUrl = refreshed;
                }
                if (!sleep(backoffMillis(idleAttempts))) return;
                continue;
            }
            if (complete) {
                buffer.markComplete();
                LOGGER.debug("Audio spool complete: bytes={} expected={} attempts={} wallMs={}",
                        buffer.written(), buffer.expected(), attempts, System.currentTimeMillis() - startedAt);
                return;
            }
            if (cancelled || !buffer.isUsable()) return;
            boolean progressed = buffer.written() > offset;
            idleAttempts = progressed ? 0 : idleAttempts + 1;
            LOGGER.warn("Audio spool connection ended early after {} ms at {} of {} bytes, reconnecting",
                    System.currentTimeMillis() - attemptStartedAt, buffer.written(), buffer.expected());
            if (idleAttempts >= MAX_IDLE_ATTEMPTS) {
                buffer.markFailed(new IOException("Audio stream stalled at " + buffer.written() + " bytes"));
                return;
            }
            if (!progressed && idleAttempts % IDLE_ATTEMPTS_PER_URL_REFRESH == 0) {
                String refreshed = refreshUrl(currentUrl);
                if (refreshed != null) currentUrl = refreshed;
            }
            if (!sleep(backoffMillis(idleAttempts))) return;
        }
        if (!cancelled && buffer.isUsable()) {
            buffer.markFailed(new IOException("Audio spool exhausted " + attempts + " attempts"));
        }
    }

    @Nullable
    private String refreshUrl(@Nullable String staleUrl) {
        SpoolBuffer.UrlRefresher current = refresher;
        if (current == null) return null;
        try {
            String refreshed = current.refresh(staleUrl);
            if (refreshed != null && !refreshed.equals(staleUrl)) {
                LOGGER.debug("Audio spool switched to a refreshed resource url");
            }
            return refreshed;
        } catch (Exception e) {
            LOGGER.warn("Audio spool failed to refresh resource url: {}", e.toString());
            return null;
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static long backoffMillis(int idleAttempts) {
        return Math.min(RETRY_BASE_MS << Math.clamp(idleAttempts - 1, 0, 4), RETRY_MAX_MS);
    }

    /** @return true when the whole object has been received */
    private boolean fetch(@Nullable String urlString, long offset) throws IOException, InterruptedException, URISyntaxException {
        if (urlString == null) throw new IOException("no resource url");
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Range", "bytes=" + offset + "-");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "MusicHUD");
        try {
            int status = connection.getResponseCode();
            String encoding = connection.getHeaderField("Transfer-Encoding");
            boolean chunked = encoding != null && encoding.toLowerCase().contains("chunked");
            long contentLength = parseLong(connection.getHeaderField("Content-Length"));
            LOGGER.debug("Audio spool request: host={} offset={} status={} chunked={} contentLength={}",
                    url.getHost(), offset, status, chunked, contentLength);
            if (status == STATUS_RANGE_NOT_SATISFIABLE) {
                LOGGER.warn("Audio spool: server reports nothing beyond {} bytes", offset);
                return true;
            }
            if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("HTTP " + status + " from " + url.getHost());
            }
            if (offset > 0 && status == HttpURLConnection.HTTP_OK) {
                throw new FatalStreamException("server ignored the range request, cannot resume at " + offset);
            }
            if (status == HttpURLConnection.HTTP_OK && contentLength > 0) {
                buffer.updateExpected(contentLength);
            }
            if (status == HttpURLConnection.HTTP_PARTIAL) {
                long[] range = parseContentRange(connection.getHeaderField("Content-Range"));
                if (range != null) {
                    if (range[0] >= 0 && range[0] != offset) {
                        throw new FatalStreamException("server resumed at " + range[0] + " instead of " + offset);
                    }
                    if (range[1] > 0) buffer.updateExpected(range[1]);
                }
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return readIntoBuffer(inputStream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private boolean readIntoBuffer(InputStream inputStream) throws IOException, InterruptedException {
        byte[] block = new byte[BLOCK_SIZE];
        while (!cancelled && buffer.isUsable()) {
            int read = inputStream.read(block);
            if (read < 0) {
                // Fixed-length bodies also end silently when the peer hangs up.
                long expected = buffer.expected();
                return expected <= 0 || buffer.written() >= expected;
            }
            if (buffer.rejects(read)) {
                throw new FatalStreamException("stream exceeded the expected size at " + buffer.written() + " bytes");
            }
            buffer.append(block, read);
            long expected = buffer.expected();
            if (expected > 0 && buffer.written() >= expected) {
                LOGGER.debug("Audio spool reached the expected size {}, dropping the connection", expected);
                return true;
            }
        }
        return false;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** @return {first byte, total bytes or 0} of a {@code bytes 0-1/2} header, or {@code null} */
    private static long[] parseContentRange(String value) {
        if (value == null) return null;
        int space = value.indexOf(' ');
        int dash = value.indexOf('-', space + 1);
        if (space < 0 || dash < 0) return null;
        try {
            long start = Long.parseLong(value.substring(space + 1, dash).trim());
            int slash = value.indexOf('/');
            long total = slash > dash ? parseLong(value.substring(slash + 1)) : 0;
            return new long[]{start, total};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A condition that cannot be fixed by reconnecting. */
    private static final class FatalStreamException extends IOException {
        FatalStreamException(String message) {
            super(message);
        }
    }
}
