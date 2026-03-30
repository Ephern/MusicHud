package indi.etern.musichud.client.ui.utils.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import icyllis.arc3d.core.*;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import indi.etern.musichud.MusicHud;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static indi.etern.musichud.MusicHud.getLogger;

public class ImageUtils {
    private static final Logger LOGGER = getLogger(ImageUtils.class);

    // 默认最大并发下载数(虚拟线程模式下可以设置更高)
    private static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 40;
    // 使用 Guava Cache
    @Getter(AccessLevel.PACKAGE)
    private static final Cache<String, ImageTextureData> cachedTexturesData = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(100)
            .removalListener(notification -> {
                Object value = notification.getValue();
                if (value instanceof ImageTextureData imageTextureData) {
                    imageTextureData.close();
                    LOGGER.debug("Removed cached texture: {}", notification.getKey());
                }
            })
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<ImageTextureData>> pendingDownloads =
            new ConcurrentHashMap<>();
    // 使用虚拟线程的 ExecutorService
    private static ExecutorService downloadExecutor;
    private static Semaphore downloadSemaphore;
    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;

    static {
        initializeVirtualThreadExecutor();
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
                        // 获取信号量许可
                        downloadSemaphore.acquire();
                        try {
                            LOGGER.debug("Starting download for URL: {} (active: {}, queued: {})",
                                    url, getActiveDownloads(), getQueuedDownloads());

                            ImageTextureData imageTextureData = downloadImage(url);
                            cachedTexturesData.put(url, imageTextureData);
                            LOGGER.debug("Successfully downloaded and cached: {}", url);
                            return imageTextureData;
                        } finally {
                            // 释放信号量许可
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
                Bitmap source = BitmapFactory.decodeStream(stream, opts);
                NativeImage nativeImage = convertBitmapToNativeImage(source);

                assert nativeImage != null;
                return getImageTextureData(url, source, nativeImage);
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

        // 创建 NativeImage
        NativeImage nativeImage = new NativeImage(width, height, false);

        // 使用 Pixmap 进行像素转换
        //noinspection UnstableApiUsage
        Pixmap srcPixmap = bitmap.getPixmap();  // 获取 Bitmap 的 Pixmap 视图
        Pixmap dstPixmap = new Pixmap(
                ImageInfo.make(width, height,
                        ColorInfo.CT_RGBA_8888,  // NativeImage 使用 RGBA
                        ColorInfo.AT_UNPREMUL,   // 非预乘 alpha
                        ColorSpace.get(ColorSpace.Named.SRGB)),
                null,  // 原生内存
                nativeImage.getPointer(),  // NativeImage 的像素地址
                width * 4  // 每行字节数
        );

        boolean success = PixelUtils.convertPixels(
                srcPixmap.getInfo(), srcPixmap.getBase(), srcPixmap.getAddress(), srcPixmap.getRowBytes(),
                dstPixmap.getInfo(), dstPixmap.getBase(), dstPixmap.getAddress(), dstPixmap.getRowBytes(),
                false
        );

        if (!success) {
            LOGGER.error("Failed to convert Bitmap to NativeImage");
            nativeImage.close();
            return null;
        }

        return nativeImage;
    }

    public static Bitmap convertNativeImageToBitmap(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();

        // 创建 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Format.RGBA_8888);

        // 使用 Pixmap 进行像素转换
        Pixmap srcPixmap = new Pixmap(
                ImageInfo.make(width, height,
                        ColorInfo.CT_RGBA_8888,
                        ColorInfo.AT_UNPREMUL,
                        ColorSpace.get(ColorSpace.Named.SRGB)),
                null,
                nativeImage.getPointer(),
                width * 4
        );

        Pixmap dstPixmap = bitmap.getPixmap();

        boolean success = PixelUtils.convertPixels(
                srcPixmap.getInfo(), srcPixmap.getBase(), srcPixmap.getAddress(), srcPixmap.getRowBytes(),
                dstPixmap.getInfo(), dstPixmap.getBase(), dstPixmap.getAddress(), dstPixmap.getRowBytes(),
                false
        );

        if (!success) {
            LOGGER.error("Failed to convert NativeImage to Bitmap");
            bitmap.recycle();
            return null;
        }

        return bitmap;
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
        Bitmap source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        NativeImage nativeImage = convertBitmapToNativeImage(source);
        if (nativeImage == null) {
            throw new RuntimeException("Failed to convert Bitmap to NativeImage");
        }
        return getImageTextureData(data, source, nativeImage);
    }

    @NotNull
    private static ImageTextureData getImageTextureData(String data, Bitmap source, NativeImage nativeImage) {
        ResourceLocation imageLocation = MusicHud.location("image_" + nativeImage.hashCode());
        AtomicReference<DynamicTexture> texture = new AtomicReference<>();
        Minecraft.getInstance().submit(() -> {
            texture.set(new DynamicTexture(() -> "image_" + source.hashCode(), nativeImage));
        }).join();
        return new ImageTextureData(data, imageLocation, texture.get(), false);
    }
}