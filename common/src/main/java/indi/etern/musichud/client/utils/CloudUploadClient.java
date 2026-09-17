package indi.etern.musichud.client.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.utils.JsonUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Client-direct cloud-drive upload. The whole file stays on the client: this only needs the
 * pre-computed MD5, the upload credentials from {@code /cloud/upload/token} and the local path.
 */
public final class CloudUploadClient {
    public static final long CHUNK_SIZE_BYTES = 4L * 1024 * 1024;
    private static final int CHUNK_RETRY_TRIES = 3;
    private static final long CHUNK_RETRY_BASE_DELAY_MILLIS = 500;

    private static final Logger LOGGER = MusicHud.getLogger(CloudUploadClient.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private CloudUploadClient() {
    }

    /** Non-2xx response from the object storage; carries the status code so callers can branch. */
    public static final class CloudUploadHttpException extends IOException {
        private final int statusCode;

        public CloudUploadHttpException(int statusCode, String body) {
            super("Cloud upload failed with HTTP " + statusCode
                    + (body == null || body.isEmpty() ? "" : ": " + body));
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    /**
     * Uploads the raw file bytes to the object-storage {@code uploadUrl} as a single request.
     * <p>
     * {@link HttpRequest.BodyPublishers#ofInputStream} alone would produce an unknown length
     * (chunked transfer encoding), so it is wrapped in a publisher that declares the real file
     * size, keeping a fixed {@code Content-Length}. The supplied stream counts bytes for
     * {@code onProgress} and aborts the request when {@code cancelRequested} becomes true.
     */
    public static CompletableFuture<Void> upload(String uploadUrl, Path file, String uploadToken, String md5,
                                                 long fileSize, String contentType, LongConsumer onProgress,
                                                 BooleanSupplier cancelRequested) {
        HttpRequest.BodyPublisher delegate = HttpRequest.BodyPublishers.ofInputStream(
                () -> new ProgressInputStream(openStream(file), onProgress, cancelRequested));
        HttpRequest.BodyPublisher body = new HttpRequest.BodyPublisher() {
            @Override
            public long contentLength() {
                return fileSize;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                delegate.subscribe(subscriber);
            }
        };

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", contentType)
                .header("Content-MD5", md5)
                .POST(body);
        if (uploadToken != null && !uploadToken.isEmpty()) {
            builder.header("x-nos-token", uploadToken);
        }

        LOGGER.debug("Uploading {} bytes to {} (single request)", fileSize, uploadUrl);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() / 100 != 2) {
                        LOGGER.error("Cloud upload failed: HTTP {} body={}", response.statusCode(), response.body());
                        throw new CompletionException(new CloudUploadHttpException(response.statusCode(), response.body()));
                    }
                    LOGGER.debug("Cloud upload finished: HTTP {}", response.statusCode());
                });
    }

    /**
     * Resumable-style upload: splits the file into {@code chunkSize} slices and posts each one to
     * the {@code offset}/{@code complete} URL. NOS requires the {@code context} returned by the
     * previous slice for any request whose offset is non-zero, so it is carried forward (from the
     * response body or an {@code x-nos-context} header). Failed slices are retried in place.
     * Blocking; call from a worker thread. Cancellation is checked between slices.
     */
    public static void uploadChunked(String uploadUrl, Path file, String uploadToken, String contentType,
                                     long fileSize, long chunkSize, LongConsumer onProgress,
                                     BooleanSupplier cancelRequested) throws Exception {
        String base = stripQuery(uploadUrl);
        String query = queryOf(uploadUrl);
        byte[] buffer = new byte[(int) Math.min(chunkSize, Integer.MAX_VALUE)];
        String context = null;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long offset = 0;
            while (offset < fileSize) {
                if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                    throw new IOException("Cloud upload cancelled");
                }
                int length = (int) Math.min(buffer.length, fileSize - offset);
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length);
                int read = 0;
                while (read < length) {
                    int n = channel.read(byteBuffer, offset + read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                boolean complete = offset + length >= fileSize;
                String chunkUrl = buildChunkUrl(base, query, offset, complete, context);
                HttpResponse<String> response = postChunkWithRetry(chunkUrl, uploadToken, contentType,
                        buffer, length, offset);
                String nextContext = extractContext(response.body());
                if (nextContext == null) {
                    nextContext = response.headers().firstValue("x-nos-context").orElse(null);
                }
                if (nextContext != null && !nextContext.isBlank()) {
                    context = nextContext;
                    LOGGER.debug("Chunk offset={} new context={}", offset, context);
                }
                long serverOffset = extractOffset(response.body(), -1);
                long next = serverOffset > offset ? serverOffset : offset + length;
                if (onProgress != null) {
                    onProgress.accept(Math.min(next, fileSize) - offset);
                }
                offset = next;
            }
        }
    }

    private static HttpResponse<String> postChunkWithRetry(String chunkUrl, String uploadToken, String contentType,
                                                           byte[] data, int length, long offset) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= CHUNK_RETRY_TRIES; attempt++) {
            try {
                return postChunk(chunkUrl, uploadToken, contentType, data, length, offset);
            } catch (CloudUploadHttpException e) {
                if (!isRetryable(e.statusCode())) {
                    throw e;
                }
                last = e;
                LOGGER.warn("Chunk offset={} failed (attempt {}/{}): {}", offset, attempt, CHUNK_RETRY_TRIES, e.getMessage());
            } catch (IOException e) {
                last = e;
                LOGGER.warn("Chunk offset={} I/O error (attempt {}/{}): {}", offset, attempt, CHUNK_RETRY_TRIES, e.getMessage());
            }
            if (attempt < CHUNK_RETRY_TRIES) {
                Thread.sleep(CHUNK_RETRY_BASE_DELAY_MILLIS * attempt);
            }
        }
        throw last;
    }

    private static HttpResponse<String> postChunk(String chunkUrl, String uploadToken, String contentType,
                                                  byte[] data, int length, long offset) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(chunkUrl))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data, 0, length));
        if (uploadToken != null && !uploadToken.isEmpty()) {
            builder.header("x-nos-token", uploadToken);
        }
        LOGGER.debug("Uploading chunk offset={} length={} URL={}", offset, length, chunkUrl);
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            LOGGER.error("Chunk offset={} failed: HTTP {} body={}", offset, response.statusCode(), response.body());
            throw new CloudUploadHttpException(response.statusCode(), response.body());
        }
        LOGGER.debug("Chunk offset={} ok: HTTP {} body={}", offset, response.statusCode(), response.body());
        return response;
    }

    private static String extractContext(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonObject object = JsonUtil.gson.fromJson(body, JsonObject.class);
            if (object == null) {
                return null;
            }
            for (String key : new String[]{"context", "uploadContext", "ctx"}) {
                JsonElement element = object.get(key);
                if (element != null && !element.isJsonNull()) {
                    String value = element.getAsString();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static long extractOffset(String body, long fallback) {
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            JsonObject object = JsonUtil.gson.fromJson(body, JsonObject.class);
            if (object != null) {
                for (String key : new String[]{"offset", "nextOffset"}) {
                    JsonElement element = object.get(key);
                    if (element != null && !element.isJsonNull()) {
                        long value = element.getAsLong();
                        if (value > 0) {
                            return value;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static String buildChunkUrl(String base, String query, long offset, boolean complete, String context) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        params.put("offset", Long.toString(offset));
        params.put("complete", Boolean.toString(complete));
        params.putIfAbsent("version", "1.0");
        if (context != null && !context.isBlank()) {
            params.put("context", URLEncoder.encode(context, StandardCharsets.UTF_8));
        }
        StringBuilder builder = new StringBuilder(base).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String stripQuery(String url) {
        int index = url.indexOf('?');
        return index < 0 ? url : url.substring(0, index);
    }

    private static String queryOf(String url) {
        int index = url.indexOf('?');
        return index < 0 ? "" : url.substring(index + 1);
    }

    public static String contentTypeOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".mpeg")) {
            return "audio/mpeg";
        }
        return "application/octet-stream";
    }

    private static InputStream openStream(Path file) {
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class ProgressInputStream extends FilterInputStream {
        private final LongConsumer onProgress;
        private final BooleanSupplier cancelRequested;

        private ProgressInputStream(InputStream in, LongConsumer onProgress, BooleanSupplier cancelRequested) {
            super(in);
            this.onProgress = onProgress;
            this.cancelRequested = cancelRequested;
        }

        @Override
        public int read() throws IOException {
            checkCancelled();
            int b = super.read();
            if (b >= 0 && onProgress != null) {
                onProgress.accept(1L);
            }
            return b;
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            checkCancelled();
            int n = super.read(b, off, len);
            if (n > 0 && onProgress != null) {
                onProgress.accept(n);
            }
            return n;
        }

        private void checkCancelled() throws IOException {
            if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                throw new IOException("Cloud upload cancelled");
            }
        }
    }
}
