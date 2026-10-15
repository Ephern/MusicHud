package indi.etern.musichud.client.utils.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.style.DynamicDrawableSpan;
import icyllis.modernui.text.style.ImageSpan;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static indi.etern.musichud.MusicHud.getLogger;

public class ImageUtils {
    private static final Logger LOGGER = getLogger(ImageUtils.class);

    private static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 40;
    @Getter(AccessLevel.PACKAGE)
    private static final Cache<String, ImageTextureData> cachedTexturesData = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(256)
            .build();
    private static final Cache<String, byte[]> cachedRemoteBytes = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .maximumWeight(64L * 1024 * 1024)
            .weigher((Weigher<String, byte[]>) (key, value) -> value.length)
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingDownloads =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<ImageTextureData>> pendingDefaultProcessed =
            new ConcurrentHashMap<>();
    private static final Map<String, Image> cachedIconImageMap = new ConcurrentHashMap<>();
    private static final BiFunction<String, InputStream, ImageTextureData> DEFAULT_PROCESSOR = (url, inputStream) -> {
        var opts = new BitmapFactory.Options();
        opts.inPreferredFormat = Bitmap.Format.RGBA_8888;

        try (Bitmap source = BitmapFactory.decodeStream(inputStream, opts)) {
            ImageTextureData imageTextureData = getImageTextureData(url, source);
            cachedTexturesData.put(url, imageTextureData);
            return imageTextureData;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
    private static ExecutorService downloadExecutor;
    private static Semaphore downloadSemaphore;
    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;

    static {
        initializeVirtualThreadExecutor();
    }

    private static void initializeVirtualThreadExecutor() {
        downloadExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("bitmap-download-", 0)
                        .factory()
        );

        downloadSemaphore = new Semaphore(maxConcurrentDownloads);

        LOGGER.info("Initialized virtual thread executor for bitmap downloads with max concurrent: {}",
                maxConcurrentDownloads);
    }

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

    public static int getActiveDownloads() {
        return maxConcurrentDownloads - downloadSemaphore.availablePermits();
    }

    public static int getQueuedDownloads() {
        return downloadSemaphore.getQueueLength();
    }

    public static CompletableFuture<ImageTextureData> downloadAsync(String url) {
        ImageTextureData cached = cachedTexturesData.getIfPresent(url);
        if (cached != null) {
            LOGGER.debug("Cache hit for URL: {}", url);
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<ImageTextureData> placeholder = new CompletableFuture<>();
        CompletableFuture<ImageTextureData> existing = pendingDefaultProcessed.putIfAbsent(url, placeholder);
        if (existing != null) {
            return existing;
        }
        try {
            downloadAsync(url, DEFAULT_PROCESSOR).whenComplete((result, ex) -> {
                if (ex == null) {
                    placeholder.complete(result);
                } else {
                    placeholder.completeExceptionally(ex);
                }
                pendingDefaultProcessed.remove(url, placeholder);
            });
        } catch (Throwable t) {
            placeholder.completeExceptionally(t);
            pendingDefaultProcessed.remove(url, placeholder);
        }
        return placeholder;
    }

    public static <R> CompletableFuture<R> downloadAsync(String url, BiFunction<String, InputStream, R> streamProcessor) {
        return downloadBytes(url).thenApplyAsync(bytes -> {
            LOGGER.debug("Successfully downloaded: {}", url);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                R r = streamProcessor.apply(url, in);
                LOGGER.debug("Successfully processed: {}", url);
                return r;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, downloadExecutor);
    }

    /**
     * Downloads the raw bytes of {@code url} once (shared with every other consumer of the same URL,
     * including the full-size image used by JMTC), decodes them and downscales to fit within
     * {@code targetW x targetH} preserving aspect ratio, then caches the resulting texture under
     * {@code url#WxH}. The original {@link #downloadAsync(String)} pipeline is untouched.
     */
    public static CompletableFuture<ImageTextureData> downloadScaledAsync(String url, int targetW, int targetH) {
        String key = url + "#" + targetW + "x" + targetH;
        ImageTextureData cached = cachedTexturesData.getIfPresent(key);
        if (cached != null) {
            LOGGER.debug("Cache hit for scaled URL: {}", key);
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<ImageTextureData> placeholder = new CompletableFuture<>();
        CompletableFuture<ImageTextureData> existing = pendingDefaultProcessed.putIfAbsent(key, placeholder);
        if (existing != null) {
            return existing;
        }
        try {
            downloadBytes(url).thenApplyAsync(bytes -> {
                try {
                    Bitmap scaled = decodeScaled(bytes, targetW, targetH);
                    ImageTextureData data = getImageTextureData(key, scaled);
                    scaled.close();
                    cachedTexturesData.put(key, data);
                    LOGGER.debug("Successfully processed scaled: {}", key);
                    return data;
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, downloadExecutor).whenComplete((result, ex) -> {
                if (ex == null) {
                    placeholder.complete(result);
                } else {
                    placeholder.completeExceptionally(ex);
                }
                pendingDefaultProcessed.remove(key, placeholder);
            });
        } catch (Throwable t) {
            placeholder.completeExceptionally(t);
            pendingDefaultProcessed.remove(key, placeholder);
        }
        return placeholder;
    }

    @NotNull
    private static Bitmap decodeScaled(byte[] bytes, int targetW, int targetH) throws IOException {
        var opts = new BitmapFactory.Options();
        opts.inPreferredFormat = Bitmap.Format.RGBA_8888;
        Bitmap source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (source.isClosed()) {
            throw new IOException("Failed to decode image bytes");
        }
        int sw = source.getWidth();
        int sh = source.getHeight();
        // Never upscale; only shrink when the source is larger than the requested box.
        if (targetW <= 0 || targetH <= 0 || (sw <= targetW && sh <= targetH)) {
            return source;
        }
        double scale = Math.min((double) targetW / sw, (double) targetH / sh);
        int tw = Math.max(1, (int) Math.round(sw * scale));
        int th = Math.max(1, (int) Math.round(sh * scale));
        Bitmap scaled = downscalePremultiplied(source, tw, th);
        source.close();
        return scaled;
    }

    @NotNull
    private static Bitmap downscalePremultiplied(@NotNull Bitmap source, int tw, int th) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int[] src = new int[sw * sh];
        source.getPixels(src, 0, sw, 0, 0, sw, sh);

        int[] dst = new int[tw * th];
        for (int y = 0; y < th; y++) {
            int sy0 = (int) ((long) y * sh / th);
            int sy1 = (int) ((long) (y + 1) * sh / th);
            if (sy1 <= sy0) {
                sy1 = sy0 + 1;
            }
            for (int x = 0; x < tw; x++) {
                int sx0 = (int) ((long) x * sw / tw);
                int sx1 = (int) ((long) (x + 1) * sw / tw);
                if (sx1 <= sx0) {
                    sx1 = sx0 + 1;
                }
                long aSum = 0, rSum = 0, gSum = 0, bSum = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    int row = sy * sw;
                    for (int sx = sx0; sx < sx1; sx++) {
                        int p = src[row + sx];
                        int a = (p >>> 24) & 0xFF;
                        aSum += a;
                        // Box filter on premultiplied alpha to avoid color bleeding at edges.
                        rSum += (long) ((p >>> 16) & 0xFF) * a;
                        gSum += (long) ((p >>> 8) & 0xFF) * a;
                        bSum += (long) (p & 0xFF) * a;
                        n++;
                    }
                }
                if (n == 0 || aSum == 0) {
                    dst[y * tw + x] = 0;
                    continue;
                }
                int a = (int) (aSum / n);
                int r = clamp8((int) (rSum / aSum));
                int g = clamp8((int) (gSum / aSum));
                int b = clamp8((int) (bSum / aSum));
                dst[y * tw + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Format.RGBA_8888);
        out.setPixels(dst, 0, tw, 0, 0, tw, th);
        return out;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static CompletableFuture<byte[]> downloadBytes(String url) {
        byte[] cached = cachedRemoteBytes.getIfPresent(url);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<byte[]> placeholder = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = pendingDownloads.putIfAbsent(url, placeholder);
        if (existing != null) {
            return existing;
        }
        byte[] rechecked = cachedRemoteBytes.getIfPresent(url);
        if (rechecked != null) {
            placeholder.complete(rechecked);
            pendingDownloads.remove(url, placeholder);
            return placeholder;
        }
        CompletableFuture.supplyAsync(() -> downloadRemoteBytes(url), downloadExecutor)
                .whenComplete((bytes, ex) -> {
                    if (ex == null) {
                        cachedRemoteBytes.put(url, bytes);
                        placeholder.complete(bytes);
                    } else {
                        placeholder.completeExceptionally(ex);
                    }
                    pendingDownloads.remove(url, placeholder);
                });
        return placeholder;
    }

    private static byte[] downloadRemoteBytes(String url) {
        downloadSemaphore.acquireUninterruptibly();
        try {
            HttpURLConnection connection = null;
            try {
                LOGGER.debug("Starting download for URL: {} (active: {}, queued: {})",
                        url, getActiveDownloads(), getQueuedDownloads());

                URL imageUrl = URI.create(url).toURL();
                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }

                String contentType = connection.getContentType();
                LOGGER.debug("Downloading bitmap from {}, Content-Type: {}", url, contentType);

                try (InputStream in = connection.getInputStream()) {
                    return in.readAllBytes();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to download image from {} : {}", url, e.getMessage());
                throw new CompletionException(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        } finally {
            downloadSemaphore.release();
        }
    }

    public static NativeImage convertBitmapToNativeImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);

        try (Bitmap wrap = Bitmap.wrap(
                nativeImage.getPointer(),
                width * 4,
                null,
                width,
                height,
                Bitmap.Format.RGBA_8888,
                false,
                ColorSpaces.SRGB)
        ) {
            wrap.setPixels(bitmap, 0, 0, 0, 0, width, height);
        }
        return nativeImage;
    }

    public static Bitmap convertNativeImageToBitmap(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();

        return Bitmap.wrap(nativeImage.getPointer(),
                width * 4,
                null,
                width,
                height,
                Bitmap.Format.RGBA_8888,
                false,
                ColorSpaces.SRGB);
    }

    @SuppressWarnings("unused")
    public static void cleanup() {
        cachedTexturesData.invalidateAll();
        cachedRemoteBytes.invalidateAll();
        pendingDownloads.clear();
        pendingDefaultProcessed.clear();
        cachedIconImageMap.clear();

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
        return String.format("Textures: %d, Cached downloads: %d, Pending downloads: %d, Active: %d, Queued: %d",
                cachedTexturesData.size(),
                cachedRemoteBytes.size(),
                pendingDownloads.size(),
                getActiveDownloads(),
                getQueuedDownloads());
    }

    @SneakyThrows
    public static ImageTextureData loadBase64(String data) {
        String base64Data = data.split(",")[1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        try (Bitmap source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length)) {
            return getImageTextureData(data, source);
        }
    }

    @NotNull
    private static ImageTextureData getImageTextureData(String data, Bitmap source) {
        AtomicReference<DynamicTexture> texture = new AtomicReference<>();
        if (RenderSystem.isOnRenderThread()) {
            texture.set(new DynamicTexture(() -> "image_" + source.hashCode(), convertBitmapToNativeImage(source)));
        } else {
            Minecraft.getInstance().submit(() ->
                    texture.set(new DynamicTexture(() -> "image_" + source.hashCode(), convertBitmapToNativeImage(source)))
            ).join();
        }
        return new ImageTextureData(data, texture.get());
    }

    public static @NotNull ImageSpan getIconSpan(Image image) {
        //noinspection UnstableApiUsage
        Context context = UIManager.getInstance().getDecorView().getContext();
        ImageSpan imageSpan = new ImageSpan(context, image, DynamicDrawableSpan.ALIGN_CENTER) {
            @Override
            public int getSize(@NonNull TextPaint paint, CharSequence text,
                               int start, int end, @Nullable FontMetricsInt fm) {
                Drawable d = getDrawable();
                int origW = d.getIntrinsicWidth();
                int origH = d.getIntrinsicHeight();
                if (origW <= 0 || origH <= 0) return 0;

                FontMetricsInt pFm = paint.getFontMetricsInt();
                int iconHeight = -pFm.ascent;

                int newWidth = Math.max(1, Math.round((float) iconHeight * origW / origH));

                d.setBounds(0, 0, newWidth, iconHeight);

                if (fm != null) {
                    fm.ascent = -iconHeight;
                    fm.descent = 0;
                }
                return newWidth;
            }
        };
        if (imageSpan.getDrawable() instanceof ImageDrawable imageDrawable) {
            imageDrawable.setFilter(true);
        }
        return imageSpan;
    }

    public static @Nullable Image getImageFromResource(String resourceName) {
        return cachedIconImageMap.computeIfAbsent(resourceName, (s) -> {
            try (InputStream iconResourceStream = MusicHud.class.getResourceAsStream(s)) {
                if (iconResourceStream != null) {
                    return Image.createTextureFromBitmap(BitmapFactory.decodeStream(iconResourceStream));
                } else {
                    return null;
                }
            } catch (Exception ignored) {
                return null;
            }
        });
    }
}