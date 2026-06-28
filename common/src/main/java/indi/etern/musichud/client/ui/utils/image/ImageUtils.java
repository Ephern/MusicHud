package indi.etern.musichud.client.ui.utils.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.MusicHud;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static indi.etern.musichud.MusicHud.getLogger;

public class ImageUtils {
    private static final Logger LOGGER = getLogger(ImageUtils.class);
    private static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 40;
    @Getter(AccessLevel.PACKAGE)
    private static final Cache<String, ImageTextureData> cachedTexturesData = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(64)
            .build();
    private static final ConcurrentHashMap<PendingKey, CompletableFuture<?>> pendingDownloads =
            new ConcurrentHashMap<>();
    private static final ExecutorService downloadExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("image-download-", 0).factory()
    );
    private static final Semaphore downloadSemaphore = new Semaphore(DEFAULT_MAX_CONCURRENT_DOWNLOADS);
    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;

    @SuppressWarnings("unused")
    public static void setMaxConcurrentDownloads(int maxDownloads) {
        if (maxDownloads <= 0) {
            throw new IllegalArgumentException("Max concurrent downloads must be positive");
        }
        int oldMax = maxConcurrentDownloads;
        maxConcurrentDownloads = maxDownloads;
        int diff = maxDownloads - oldMax;
        if (diff > 0) {
            downloadSemaphore.release(diff);
        } else if (diff < 0) {
            downloadSemaphore.acquireUninterruptibly(-diff);
        }
    }

    public static int getActiveDownloads() {
        return maxConcurrentDownloads - downloadSemaphore.availablePermits();
    }

    public static int getQueuedDownloads() {
        return downloadSemaphore.getQueueLength();
    }

    public static CompletableFuture<ImageTextureData> downloadAsync(String url) {
        ImageTextureData cached = cachedTexturesData.getIfPresent(url);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return downloadAsync(url, inputStream -> {
            try {
                return getImageTextureData(url, NativeImage.read(inputStream));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, true);
    }

    public static <R> CompletableFuture<R> downloadAsync(String url, Function<InputStream, R> streamProcessor, boolean computable) {
        if (computable) {
            PendingKey key = new PendingKey(url, streamProcessor);
            //noinspection unchecked
            return (CompletableFuture<R>) pendingDownloads.computeIfAbsent(key, k ->
                    downloadAsyncInternal(url, streamProcessor).whenComplete((result, ex) -> pendingDownloads.remove(key))
            );
        }
        return downloadAsyncInternal(url, streamProcessor);
    }

    private static <R> @NotNull CompletableFuture<R> downloadAsyncInternal(String url, Function<InputStream, R> streamProcessor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                downloadSemaphore.acquire();
                try {
                    return downloadImage(url, streamProcessor);
                } finally {
                    downloadSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException("Download interrupted", e);
            } catch (Exception e) {
                LOGGER.error("Failed to download image from {} : {}", url, e.getMessage());
                throw new CompletionException(e);
            }
        }, downloadExecutor);
    }

    private static <R> R downloadImage(String url, Function<InputStream, R> streamProcessor) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL imageUrl = URI.create(url).toURL();
            connection = (HttpURLConnection) imageUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            try (InputStream stream = connection.getInputStream()) {
                return streamProcessor.apply(stream);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @SuppressWarnings("unused")
    public static void cleanup() {
        cachedTexturesData.invalidateAll();
        pendingDownloads.clear();
        downloadExecutor.shutdownNow();
    }

    public static String getCacheStats() {
        return String.format("Cache size: %d, Pending downloads: %d, Active: %d, Queued: %d",
                cachedTexturesData.size(), pendingDownloads.size(), getActiveDownloads(), getQueuedDownloads());
    }

    @SneakyThrows
    public static ImageTextureData loadBase64(String data) {
        String base64Data = data.split(",")[1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        return getImageTextureData(data, NativeImage.read(new ByteArrayInputStream(imageBytes)));
    }

    @NotNull
    private static ImageTextureData getImageTextureData(String data, NativeImage image) {
        AtomicReference<DynamicTexture> texture = new AtomicReference<>();
        Minecraft.getInstance().submit(() -> texture.set(new DynamicTexture(() -> "image_" + data.hashCode(), image))).join();
        ImageTextureData textureData = new ImageTextureData(data, texture.get());
        cachedTexturesData.put(data, textureData);
        return textureData;
    }

    record PendingKey(String url, Function<InputStream, ?> consumer) {
    }
}
