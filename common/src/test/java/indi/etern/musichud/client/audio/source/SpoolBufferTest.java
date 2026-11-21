package indi.etern.musichud.client.audio.source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SpoolBufferTest {
    private static Path tempRoot;
    private HttpServer server;
    private byte[] payload;
    private final AtomicReference<String> lastRange = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeAll
    static void isolateTempRoot() throws IOException {
        tempRoot = Files.createTempDirectory("musichud-spool-test-");
        System.setProperty("music_hud.temp.root", tempRoot.toString());
    }

    @AfterAll
    static void cleanupTempRoot() {
        System.clearProperty("music_hud.temp.root");
        TempRootCleanup.delete(tempRoot);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        lastRange.set(null);
        requests.set(0);
    }

    @Test
    void resumesTruncatedTransfer() {
        payload = randomBytes(200_000);
        String url = startServer(50_000);
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            try (SpoolBuffer spool = SpoolBuffer.open(url, payload.length, null, "truncated")) {
                assertNotNull(spool);
                assertArrayEquals(payload, readAll(spool.openStream(), payload.length));
                assertTrue(spool.isComplete());
            }
        });
        assertEquals("bytes=50000-", lastRange.get());
        assertTrue(requests.get() >= 2);
    }

    @Test
    void resumesWithUnknownExpectedSize() {
        payload = randomBytes(120_000);
        String url = startServer(40_000);
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            try (SpoolBuffer spool = SpoolBuffer.open(url, 0, null, "unknown-size")) {
                assertNotNull(spool);
                assertArrayEquals(payload, readAll(spool.openStream(), payload.length));
                assertTrue(spool.isComplete());
            }
        });
        assertEquals("bytes=40000-", lastRange.get());
    }

    @Test
    void readerBlocksUntilMoreDataArrives() {
        payload = randomBytes(120_000);
        String url = startServer(10_000);
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            try (SpoolBuffer spool = SpoolBuffer.open(url, payload.length, null, "blocking")) {
                assertNotNull(spool);
                InputStream stream = spool.openStream();
                byte[] head = readAll(stream, 10_000);
                assertEquals(10_000, head.length, "reader must return the first fragment before the producer resumes");
                byte[] tail = readAll(stream, payload.length - head.length);
                byte[] all = new byte[payload.length];
                System.arraycopy(head, 0, all, 0, head.length);
                System.arraycopy(tail, 0, all, head.length, tail.length);
                assertArrayEquals(payload, all);
            }
        });
    }

    @Test
    void closeReleasesPendingReader() throws Exception {
        payload = randomBytes(10_000);
        String url = startServerHanging(10_000);
        SpoolBuffer spool = SpoolBuffer.open(url, 100_000, null, "hanging");
        assertNotNull(spool);
        InputStream stream = spool.openStream();
        assertEquals(10_000, readAll(stream, 10_000).length);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> blocked = executor.submit(() -> {
                try {
                    stream.read();
                    fail("read must fail once the spool is closed");
                } catch (IOException ignored) {
                }
            });
            Thread.sleep(200);
            spool.close();
            blocked.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            spool.close();
        }
    }

    private String startServer(int firstFragment) {
        server = createServer();
        server.createContext("/audio", exchange -> serve(exchange, firstFragment, payload.length, false));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return url();
    }

    private String startServerHanging(int firstFragment) {
        server = createServer();
        server.createContext("/audio", exchange -> serve(exchange, firstFragment, 100_000, true));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return url();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/audio";
    }

    private static HttpServer createServer() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void serve(HttpExchange exchange, int firstFragment, int declaredTotal, boolean hang) throws IOException {
        lastRange.set(exchange.getRequestHeaders().getFirst("Range"));
        int start = parseStart(lastRange.get());
        int remaining = declaredTotal - start;
        exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
        exchange.getResponseHeaders().add("Content-Range",
                "bytes " + start + "-" + (declaredTotal - 1) + "/" + declaredTotal);
        exchange.sendResponseHeaders(206, remaining);
        OutputStream body = exchange.getResponseBody();
        int available = payload.length - Math.min(start, payload.length);
        int length = requests.getAndIncrement() == 0 ? Math.min(firstFragment, available) : available;
        body.write(payload, Math.min(start, payload.length), length);
        body.flush();
        if (hang && length < remaining) {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        exchange.close();
    }

    private static int parseStart(String range) {
        if (range == null || !range.startsWith("bytes=")) return 0;
        String value = range.substring("bytes=".length());
        int dash = value.indexOf('-');
        if (dash <= 0) return 0;
        try {
            return Integer.parseInt(value.substring(0, dash));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] readAll(InputStream stream, int expected) throws IOException {
        byte[] buffer = new byte[expected];
        int count = 0;
        while (count < expected) {
            int read = stream.read(buffer, count, expected - count);
            if (read < 0) break;
            count += read;
        }
        return count == buffer.length ? buffer : Arrays.copyOf(buffer, count);
    }

    private static byte[] randomBytes(int length) {
        byte[] data = new byte[length];
        new Random(42).nextBytes(data);
        return data;
    }

    private static final class TempRootCleanup {
        static void delete(Path root) {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }
}
