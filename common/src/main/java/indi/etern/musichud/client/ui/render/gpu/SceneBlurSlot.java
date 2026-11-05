package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Surface;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.interop.InteropSource;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Per-consumer blur pipeline. The UI thread records the blur source into an arc3d surface and hands
 * its image over; the render thread crops and blurs it with {@link SceneBlur}, then publishes the
 * result so the recording that samples it sees the fresh content.
 */
public final class SceneBlurSlot {
    private static final Logger LOGGER = MusicHud.getLogger(SceneBlurSlot.class);
    private static final int RETIRE_FRAMES = 2;
    private static final Object ACTIVE_LOCK = new Object();
    private static final List<SceneBlurSlot> ACTIVE = new ArrayList<>();
    private static final List<Retired> RETIRED = new ArrayList<>();
    private static long frame;

    private final Object requestLock = new Object();

    private icyllis.arc3d.engine.Image pendingSource;
    private float u0;
    private float v0;
    private float u1 = 1f;
    private float v1 = 1f;
    private int outWidth;
    private int outHeight;
    private float sigma;
    private boolean pending;

    private Pipeline pipeline;
    private InteropSource source;
    private icyllis.arc3d.engine.Image sourceKey;
    private final PublishedSceneTexture published = new PublishedSceneTexture();
    @Getter
    private volatile boolean disabled;

    /** UI thread: references the backing image of an arc3d render target. */
    public static @Nullable icyllis.arc3d.engine.Image imageOf(@Nullable Surface surface) {
        if (!(surface instanceof GraniteSurface granite)) {
            return null;
        }
        var proxy = granite.getBackingTarget();
        return proxy == null ? null : RefCnt.create(proxy.getImage());
    }

    public void register() {
        synchronized (ACTIVE_LOCK) {
            if (!ACTIVE.contains(this)) {
                ACTIVE.add(this);
            }
        }
    }

    public void unregister() {
        synchronized (ACTIVE_LOCK) {
            ACTIVE.remove(this);
        }
        discardPending();
        releaseNow();
    }

    /** Permanently stops this slot after a failure. */
    public void shutdown() {
        disabled = true;
        unregister();
    }

    /** UI thread: hands over a reference to the blur source region. */
    public void request(@Nullable icyllis.arc3d.engine.Image image,
                        float u0, float v0, float u1, float v1,
                        int outWidth, int outHeight, float sigma) {
        if (image == null || disabled || outWidth < 1 || outHeight < 1 || !(sigma > 0f)) {
            if (image != null) {
                image.unref();
            }
            return;
        }
        synchronized (requestLock) {
            if (pendingSource != null) {
                pendingSource.unref();
            }
            pendingSource = image;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.outWidth = outWidth;
            this.outHeight = outHeight;
            this.sigma = sigma;
            pending = true;
        }
    }

    public @Nullable Image image() {
        return disabled ? null : published.image();
    }

    /** UI thread: drops the published result, so the next composite waits for a fresh pass. */
    public void invalidatePublished() {
        published.publish(null);
        published.release();
    }

    /** True when at least one registered slot has a queued blur. */
    public static boolean hasPending() {
        synchronized (ACTIVE_LOCK) {
            for (SceneBlurSlot slot : ACTIVE) {
                synchronized (slot.requestLock) {
                    if (slot.pending) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void discardPending() {
        synchronized (requestLock) {
            if (pendingSource != null) {
                pendingSource.unref();
                pendingSource = null;
            }
            pending = false;
        }
    }

    /** Render thread: executes every pending blur. */
    public static void runAll(SceneGpuContext gpu) {
        List<SceneBlurSlot> slots;
        synchronized (ACTIVE_LOCK) {
            frame++;
            purgeRetired();
            if (ACTIVE.isEmpty()) {
                return;
            }
            slots = new ArrayList<>(ACTIVE);
        }
        for (SceneBlurSlot slot : slots) {
            slot.run(gpu);
        }
    }

    private static void purgeRetired() {
        for (Iterator<Retired> iterator = RETIRED.iterator(); iterator.hasNext(); ) {
            Retired entry = iterator.next();
            if (frame - entry.frame >= RETIRE_FRAMES) {
                entry.release.run();
                iterator.remove();
            }
        }
    }

    private static void scheduleRelease(Runnable release) {
        synchronized (ACTIVE_LOCK) {
            RETIRED.add(new Retired(frame, release));
        }
    }

    private void run(SceneGpuContext gpu) {
        if (disabled) {
            discardPending();
            return;
        }
        icyllis.arc3d.engine.Image image;
        float u0;
        float v0;
        float u1;
        float v1;
        int outWidth;
        int outHeight;
        float sigma;
        synchronized (requestLock) {
            if (!pending || pendingSource == null) {
                return;
            }
            image = pendingSource;
            pendingSource = null;
            pending = false;
            u0 = this.u0;
            v0 = this.v0;
            u1 = this.u1;
            v1 = this.v1;
            outWidth = this.outWidth;
            outHeight = this.outHeight;
            sigma = this.sigma;
        }
        InteropSource input = null;
        try {
            input = useSource(image);
            if (input == null) {
                return;
            }
            SceneInterop.beginRead(input);
            Pipeline current = usePipeline(gpu, outWidth, outHeight, sigma);
            SceneTextureRef blurSource = input;
            int blurWidth = input.width();
            int blurHeight = input.height();
            boolean cropped = u0 > 0f || v0 > 0f || u1 < 1f || v1 < 1f
                    || blurWidth != outWidth || blurHeight != outHeight;
            if (cropped) {
                blurSource = current.crop.applyRef(gpu, input, blurWidth, blurHeight,
                        u0, v0, u1, v1, outWidth, outHeight);
                if (blurSource == null) {
                    return;
                }
                blurWidth = outWidth;
                blurHeight = outHeight;
            }
            SceneBlur.PendingBlur pendingBlur = current.blur.prepare(gpu,
                    blurWidth, blurHeight, sigma);
            if (pendingBlur == null || !pendingBlur.apply(gpu, blurSource, blurWidth, blurHeight)) {
                return;
            }
            SceneGpuTexture target = pendingBlur.target();
            current.result = target;
            published.publish(target);
        } catch (Throwable failure) {
            // A broken slot must not take the whole UI down; the consumer falls back to a sharp draw.
            LOGGER.error("Disabling backdrop blur after an error", failure);
            shutdown();
        } finally {
            if (input != null) {
                SceneInterop.endRead(input);
            }
        }
    }

    private @Nullable InteropSource useSource(icyllis.arc3d.engine.Image image) {
        if (source != null && sourceKey == image) {
            image.unref();
            return source;
        }
        InteropSource wrapped = SceneInterop.importImage(image, InteropUsage.SAMPLE);
        if (wrapped == null) {
            return null;
        }
        InteropSource previous = source;
        source = wrapped;
        sourceKey = image;
        if (previous != null) {
            scheduleRelease(previous::close);
        }
        return wrapped;
    }

    private Pipeline usePipeline(SceneGpuContext gpu, int width, int height, float sigma) {
        int level = SceneBlur.levelFor(width, height, sigma);
        Pipeline current = pipeline;
        if (current != null && current.width == width && current.height == height
                && current.level == level && current.gpu == gpu) {
            return current;
        }
        Pipeline created = new Pipeline(gpu, width, height, level);
        pipeline = created;
        if (current != null) {
            scheduleRelease(current::release);
        }
        return created;
    }

    private void releaseNow() {
        discardPending();
        InteropSource previousSource = source;
        source = null;
        sourceKey = null;
        if (previousSource != null) {
            scheduleRelease(previousSource::close);
        }
        Pipeline previousPipeline = pipeline;
        pipeline = null;
        if (previousPipeline != null) {
            scheduleRelease(previousPipeline::release);
        }
        published.publish(null);
        published.release();
    }

    private static final class Pipeline {
        final SceneGpuContext gpu;
        final int width;
        final int height;
        final int level;
        final SceneBlur blur = new SceneBlur(SceneTextureFormat.RGBA8);
        final SceneBlurCrop crop = new SceneBlurCrop(SceneTextureFormat.RGBA8);
        SceneGpuTexture result;

        Pipeline(SceneGpuContext gpu, int width, int height, int level) {
            this.gpu = gpu;
            this.width = width;
            this.height = height;
            this.level = level;
        }

        void release() {
            blur.release();
            crop.release();
        }
    }

    private record Retired(long frame, Runnable release) {
    }
}
