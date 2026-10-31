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
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static indi.etern.musichud.MusicHud.getLogger;

public class ImageUtils {
    private static final Logger LOGGER = getLogger(ImageUtils.class);

    private static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 40;
    private static final int CANONICAL_MAX = 2048;
    private static final long SOURCE_CACHE_BUDGET = 128L * 1024 * 1024;
    private static final int MAX_DERIVED_VARIANTS = 4;
    @Getter(AccessLevel.PACKAGE)
    private static final Cache<String, ImageTextureData> cachedTexturesData = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumWeight(64L * 1024 * 1024)
            .weigher((Weigher<String, ImageTextureData>) (key, value) -> {
                // Weighing a closed texture throws; a throwing weigher fails the whole cache.
                try {
                    DynamicTexture texture = value.getTexture();
                    NativeImage pixels = texture == null ? null : texture.getPixels();
                    return pixels == null ? 1 : Math.max(1, pixels.getWidth() * pixels.getHeight() * 4);
                } catch (RuntimeException closed) {
                    return 1;
                }
            })
            .build();
    /** Decoded pixels per URL at the largest size requested so far. Callers must never close them. */
    private static final Cache<String, SourceEntry> sourceCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumWeight(SOURCE_CACHE_BUDGET)
            .weigher((Weigher<String, SourceEntry>) (key, entry) -> entry.weightBytes())
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<Bitmap>> pendingSourceDecodes =
            new ConcurrentHashMap<>();
    private static final AtomicLong cacheHits = new AtomicLong();
    private static final AtomicLong cachePromotions = new AtomicLong();
    private static final AtomicLong cacheDerivations = new AtomicLong();
    private static final AtomicLong cacheDecodes = new AtomicLong();
    private static final AtomicLong uiUploads = new AtomicLong();
    private static final Cache<String, byte[]> cachedRemoteBytes = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumWeight(64L * 1024 * 1024)
            .weigher((Weigher<String, byte[]>) (key, value) -> value.length)
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingDownloads =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<ImageTextureData>> pendingDefaultProcessed =
            new ConcurrentHashMap<>();
    private static final Map<String, Image> cachedIconImageMap = new ConcurrentHashMap<>();
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
            // The largest size the cache keeps, so later smaller requests are derived from it.
            loadBitmap(url, CANONICAL_MAX, CANONICAL_MAX).thenApplyAsync(bitmap ->
                    createTexture(url, bitmap), downloadExecutor
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    cachedTexturesData.put(url, result);
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

    /** Borrowed pixels covering at least {@code minW x minH}; never close the result. */
    public static CompletableFuture<Bitmap> loadBitmap(String url, int minW, int minH) {
        int requestW = Math.max(1, minW);
        int requestH = Math.max(1, minH);
        SourceEntry entry = sourceCache.asMap().computeIfAbsent(url, ignored -> new SourceEntry());
        synchronized (entry) {
            Bitmap ready = borrow(url, entry, requestW, requestH);
            if (ready != null) {
                cacheHits.incrementAndGet();
                return CompletableFuture.completedFuture(ready);
            }
            if (entry.saturated) {
                cacheHits.incrementAndGet();
                return CompletableFuture.completedFuture(entry.canonical);
            }
        }
        //noinspection MathClampMigration
        int wanted = Math.min(CANONICAL_MAX, Math.max(requestW, requestH));
        int target;
        synchronized (entry) {
            target = Math.max(wanted, entry.canonicalBox);
        }
        return decodeSource(url, target).thenApplyAsync(bitmap -> {
            synchronized (entry) {
                Bitmap previous = entry.canonical;
                if (previous == null || entry.canonicalBox <= target) {
                    if (entry.canonical != null && entry.canonicalBox < target) {
                        cachePromotions.incrementAndGet();
                        invalidateTextures(url);
                    }
                    entry.canonical = bitmap;
                    entry.canonicalBox = target;
                    // decodeScaled never upscales, so a smaller result means the source is that small.
                    boolean noProgress = previous != null
                            && previous.getWidth() == bitmap.getWidth()
                            && previous.getHeight() == bitmap.getHeight();
                    entry.saturated = bitmap.getWidth() < target || bitmap.getHeight() < target
                            || noProgress;
                    entry.generation++;
                    entry.derived.clear();
                    entry.canonicalBytes = (long) bitmap.getWidth() * bitmap.getHeight() * 4;
                    entry.derivedBytes = 0;
                    sourceCache.put(url, entry);
                }
                //noinspection resource
                Bitmap borrowed = borrow(url, entry, requestW, requestH);
                return borrowed != null ? borrowed : bitmap;
            }
        }, downloadExecutor);
    }

    /** @return a borrowed bitmap within the request, or {@code null} when the canonical is too small. */
    private static Bitmap borrow(String url, SourceEntry entry, int minW, int minH) {
        Bitmap canonical = entry.canonical;
        if (canonical == null || canonical.isClosed()) {
            return null;
        }
        if (entry.saturated) {
            return canonical;
        }
        if (canonical.getWidth() < minW || canonical.getHeight() < minH) {
            return null;
        }
        // Within 4/3 the extra copy adds no detail.
        if (canonical.getWidth() <= minW * 4 / 3 && canonical.getHeight() <= minH * 4 / 3) {
            return canonical;
        }
        //noinspection MathClampMigration
        int box = Math.min(CANONICAL_MAX, Math.max(minW, minH));
        Bitmap derived = entry.derived.get(box);
        if (derived != null && !derived.isClosed()) {
            return derived;
        }
        derived = deriveScaled(canonical, minW, minH);
        cacheDerivations.incrementAndGet();
        entry.derived.put(box, derived);
        while (entry.derived.size() > MAX_DERIVED_VARIANTS) {
            Iterator<Integer> eldest = entry.derived.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        entry.derivedBytes = 0;
        for (Bitmap variant : entry.derived.values()) {
            entry.derivedBytes += variant.getWidth() * variant.getHeight() * 4L;
        }
        // Re-put so the weighted cache sees the new total instead of the install-time snapshot.
        sourceCache.put(url, entry);
        return derived;
    }

    /** Decodes {@code url} at {@code box}, single-flight per (url, box). */
    private static CompletableFuture<Bitmap> decodeSource(String url, int box) {
        String key = url + "#src" + box;
        CompletableFuture<Bitmap> placeholder = new CompletableFuture<>();
        CompletableFuture<Bitmap> existing = pendingSourceDecodes.putIfAbsent(key, placeholder);
        if (existing != null) {
            return existing;
        }
        cacheDecodes.incrementAndGet();
        downloadBytes(url).thenApplyAsync(bytes -> {
            try {
                Bitmap decoded = decodeScaled(bytes, box, box);
                SourceEntry entry = sourceCache.getIfPresent(url);
                if (entry != null) {
                    boolean pixelArt = detectPixelArt(decoded);
                    synchronized (entry) {
                        entry.pixelArt = pixelArt;
                    }
                }
                return decoded;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, downloadExecutor).whenComplete((bitmap, error) -> {
            if (error == null) {
                placeholder.complete(bitmap);
            } else {
                placeholder.completeExceptionally(error);
            }
            pendingSourceDecodes.remove(key, placeholder);
        });
        return placeholder;
    }

    /**
     * Small palette plus long runs of identical pixels, sampled from the centre. Only picks the
     * nearest filter for enlarged images.
     */
    private static boolean detectPixelArt(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width < 16 || height < 16) {
            return false;
        }
        int sample = Math.min(256, Math.min(width, height));
        int originX = (width - sample) / 2;
        int originY = (height - sample) / 2;
        int[] pixels = new int[sample * sample];
        try {
            bitmap.getPixels(pixels, 0, sample, originX, originY, sample, sample);
        } catch (RuntimeException failure) {
            return false;
        }
        java.util.HashSet<Integer> colors = new java.util.HashSet<>();
        int identical = 0;
        int compared = 0;
        for (int y = 0; y < sample; y++) {
            int row = y * sample;
            for (int x = 0; x < sample; x++) {
                int color = pixels[row + x];
                if (colors.size() <= 512) {
                    colors.add(color);
                }
                if (x + 1 < sample) {
                    compared++;
                    if (pixels[row + x + 1] == color) identical++;
                }
                if (y + 1 < sample) {
                    compared++;
                    if (pixels[row + sample + x] == color) identical++;
                }
            }
        }
        return compared > 0 && colors.size() <= 256 && identical * 10 >= compared * 6;
    }

    /** @return true when the decoded image looks like hard-edged pixel art. */
    public static boolean isPixelArt(String url) {
        SourceEntry entry = sourceCache.getIfPresent(url);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return entry.pixelArt;
        }
    }

    /** Drops cached textures that were derived from a superseded (smaller) source. */
    private static void invalidateTextures(String url) {
        String prefix = url + "#";
        cachedTexturesData.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static final class SourceEntry {
        Bitmap canonical;
        int canonicalBox;
        long generation;
        boolean pixelArt;
        boolean saturated;
        long canonicalBytes;
        long derivedBytes;
        final LinkedHashMap<Integer, Bitmap> derived = new LinkedHashMap<>(8, 0.75f, true);

        int weightBytes() {
            synchronized (this) {
                return (int) Math.min(Integer.MAX_VALUE, canonicalBytes + derivedBytes);
            }
        }
    }

    /** Cache counters for the render stats line. */
    public static String describeCache() {
        // No per-entry scan: it would take the per-URL locks from the render thread.
        return " | img: urls=" + sourceCache.size()
                + " hit=" + cacheHits.get() + " decode=" + cacheDecodes.get()
                + " promote=" + cachePromotions.get() + " filter=" + cacheDerivations.get() + " ui=" + uiUploads.get();
    }

    /** Counts ModernUI texture uploads, i.e. how often a widget actually re-created its image. */
    public static void noteUiUpload() {
        uiUploads.incrementAndGet();
    }

    /** Bytes are downloaded once per URL; the texture is cached under {@code url#WxH}. */
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
            loadBitmap(url, targetW, targetH).thenApplyAsync(bitmap ->
                    createTexture(key, bitmap), downloadExecutor
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    cachedTexturesData.put(key, result);
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

    private static ImageTextureData createTexture(String key, Bitmap bitmap) {
        ImageTextureData data = getImageTextureData(key, bitmap);
        LOGGER.debug("Successfully processed scaled: {}", key);
        return data;
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
        Bitmap scaled = deriveScaled(source, targetW, targetH);
        source.close();
        return scaled;
    }

    /** Box-filters {@code source} into the requested box, preserving its aspect ratio. */
    @NotNull
    private static Bitmap deriveScaled(@NotNull Bitmap source, int targetW, int targetH) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        double scale = Math.min((double) targetW / sw, (double) targetH / sh);
        int tw = Math.max(1, (int) Math.round(sw * scale));
        int th = Math.max(1, (int) Math.round(sh * scale));
        return downscalePremultiplied(source, tw, th);
    }

    /**
     * Every requested box from one decode. Unlike {@link #downloadScaledAsync} the caller owns the
     * textures and must close them.
     */
    public static CompletableFuture<ImageTextureData[]> downloadScaledSetOwned(String url, int[] boxes) {
        if (boxes.length == 0) {
            return CompletableFuture.completedFuture(new ImageTextureData[0]);
        }
        int largest = 0;
        for (int box : boxes) {
            largest = Math.max(largest, box);
        }
        // One decode, shared with every other consumer of this URL.
        return loadBitmap(url, largest, largest).thenApplyAsync(base -> {
            ImageTextureData[] result = new ImageTextureData[boxes.length];
            for (int i = 0; i < boxes.length; i++) {
                boolean derived = base.getWidth() > boxes[i] || base.getHeight() > boxes[i];
                Bitmap pixels = derived ? deriveScaled(base, boxes[i], boxes[i]) : base;
                try {
                    result[i] = getImageTextureData(url + "#owned" + boxes[i], pixels);
                } finally {
                    if (derived) {
                        pixels.close();
                    }
                }
            }
            return result;
        }, downloadExecutor);
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

    /** Warms the raw byte cache so the next decode does not wait for the network. */
    public static void prefetchBytes(String url) {
        if (url == null || url.isBlank()) return;
        downloadBytes(url);
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
            try (InputStream iconResourceStream = MusicHud.getResourceAsStream(s)) {
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
