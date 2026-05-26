package indi.etern.musichud.client.ui.utils.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import icyllis.arc3d.core.ColorSpace;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static indi.etern.musichud.MusicHud.getLogger;

public class ImageUtils {
    private static final Logger LOGGER = getLogger(ImageUtils.class);

    private static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 40;
    @Getter(AccessLevel.PACKAGE)
    private static final Cache<String, ImageTextureData> cachedTexturesData = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(64)
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<ImageTextureData>> pendingDownloads =
            new ConcurrentHashMap<>();
    // 使用虚拟线程的 ExecutorService
    private static ExecutorService downloadExecutor;
    private static Semaphore downloadSemaphore;
    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;

    private static final VarHandle NATIVE_IMAGE_PIXELS_HANDLE;

    static {
        initializeVirtualThreadExecutor();
        VarHandle handle = null;
        try {
            Field found = null;
            for (Field f : NativeImage.class.getDeclaredFields()) {
                if (f.getType() == long.class && !Modifier.isFinal(f.getModifiers())) {
                    if (found != null) {
                        throw new IllegalStateException("Multiple long non-final fields in NativeImage");
                    }
                    found = f;
                }
            }
            if (found == null) {
                throw new IllegalStateException("No long non-final field found in NativeImage");
            }
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(NativeImage.class, MethodHandles.lookup());
            handle = lookup.unreflectVarHandle(found);
        } catch (Throwable t) {
            LOGGER.error("Failed to init VarHandle for NativeImage pixels", t);
        }
        NATIVE_IMAGE_PIXELS_HANDLE = handle;
    }

    private static long getNativeImagePixels(NativeImage image) {
        if (NATIVE_IMAGE_PIXELS_HANDLE == null) {
            throw new IllegalStateException("NativeImage pixels VarHandle not initialized");
        }
        return (long) NATIVE_IMAGE_PIXELS_HANDLE.get(image);
    }

    /**
     * 初始化虚拟线程执行器
     */
    private static void initializeVirtualThreadExecutor() {
        // 使用虚拟线程工厂创建 ExecutorService
        downloadExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("bitmap-download-", 0)
                        .factory()
        );

        // 使用 Semaphore 来限制并发数
        downloadSemaphore = new Semaphore(maxConcurrentDownloads);

        LOGGER.info("Initialized virtual thread executor for bitmap downloads with max concurrent: {}",
                maxConcurrentDownloads);
    }

    /**
     * 设置最大并发下载数
     * 虚拟线程下可以设置更高的并发数(如 50-100)
     *
     * @param maxDownloads 最大并发下载数
     */
    @SuppressWarnings("unused")
    public static void setMaxConcurrentDownloads(int maxDownloads) {
        if (maxDownloads <= 0) {
            throw new IllegalArgumentException("Max concurrent downloads must be positive");
        }

        int oldMax = maxConcurrentDownloads;
        maxConcurrentDownloads = maxDownloads;

        // 重新创建 Semaphore
        int diff = maxDownloads - oldMax;
        if (diff > 0) {
            downloadSemaphore.release(diff);
        } else if (diff < 0) {
            downloadSemaphore.acquireUninterruptibly(-diff);
        }

        LOGGER.info("Updated max concurrent downloads from {} to {}", oldMax, maxDownloads);
    }

    /**
     * 获取当前活跃的下载数
     */
    public static int getActiveDownloads() {
        return maxConcurrentDownloads - downloadSemaphore.availablePermits();
    }

    /**
     * 获取等待中的下载数
     */
    public static int getQueuedDownloads() {
        return downloadSemaphore.getQueueLength();
    }

    /**
     * 异步下载图片
     */
    public static CompletableFuture<ImageTextureData> downloadAsync(String url) {
        // 检查缓存
        ImageTextureData cached = cachedTexturesData.getIfPresent(url);
        if (cached != null) {
            LOGGER.debug("Cache hit for URL: {}", url);
            return CompletableFuture.completedFuture(cached);
        }

        // 检查是否已有正在进行的下载
        return pendingDownloads.computeIfAbsent(url, k ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        downloadSemaphore.acquire();
                        try {
                            LOGGER.debug("Starting download for URL: {} (active: {}, queued: {})",
                                    url, getActiveDownloads(), getQueuedDownloads());

                            ImageTextureData imageTextureData = downloadImage(url);
                            cachedTexturesData.put(url, imageTextureData);
                            LOGGER.debug("Successfully downloaded and cached: {}", url);
                            return imageTextureData;
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
                }, downloadExecutor).whenComplete((result, ex) -> {
                    // 下载完成后从 pending 中移除
                    pendingDownloads.remove(url);
                })
        );
    }

    /**
     * 同步下载图片(阻塞当前线程)
     */
    private static ImageTextureData downloadImage(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL imageUrl = URI.create(url).toURL();
            connection = (HttpURLConnection) imageUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "ModernUI-MC/ImageUtils");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            String contentType = connection.getContentType();
            LOGGER.debug("Downloading bitmap from {}, Content-Type: {}", url, contentType);

            try (InputStream stream = connection.getInputStream()) {
                var opts = new BitmapFactory.Options();
                opts.inPreferredFormat = Bitmap.Format.RGBA_8888;

                try (Bitmap source = BitmapFactory.decodeStream(stream, opts)){
                    return getImageTextureData(url, source);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static NativeImage convertBitmapToNativeImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);

        long pointer = getNativeImagePixels(nativeImage);

        //noinspection UnstableApiUsage
        try (Bitmap wrap = Bitmap.wrap(
                pointer,
                width * 4,
                null,
                width,
                height,
                Bitmap.Format.RGBA_8888,
                false,
                ColorSpace.get(ColorSpace.Named.SRGB)
        )){
            wrap.setPixels(bitmap, 0, 0, 0, 0, width, height);
        }
        return nativeImage;
    }

    public static Bitmap convertNativeImageToBitmap(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();

        long pointer = getNativeImagePixels(nativeImage);

        //noinspection UnstableApiUsage
        return Bitmap.wrap(pointer,
                width * 4,
                null,
                width,
                height,
                Bitmap.Format.RGBA_8888,
                false,
                ColorSpace.get(ColorSpace.Named.SRGB));
    }

    @SuppressWarnings("unused")
    public static void cleanup() {
        cachedTexturesData.invalidateAll();
        pendingDownloads.clear();

        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdown();
            try {
                if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    downloadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                downloadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.debug("Cleaned up all cached textures and shutdown download executor");
    }

    public static String getCacheStats() {
        return String.format("Cache size: %d, Pending downloads: %d, Active: %d, Queued: %d",
                cachedTexturesData.size(),
                pendingDownloads.size(),
                getActiveDownloads(),
                getQueuedDownloads());
    }

    @SneakyThrows
    public static ImageTextureData loadBase64(String data) {
        String base64Data = data.split(",")[1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        try (Bitmap source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length)){
            return getImageTextureData(data, source);
        }
    }

    @NotNull
    private static ImageTextureData getImageTextureData(String data, Bitmap source) {
//        ResourceLocation imageLocation = MusicHud.location("image_" + source.hashCode());
        AtomicReference<DynamicTexture> texture = new AtomicReference<>();
        Minecraft.getInstance().submit(() -> {
            texture.set(new DynamicTexture(convertBitmapToNativeImage(source)));
        }).join();
        return new ImageTextureData(data, texture.get());
    }
}