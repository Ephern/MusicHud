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
 * <p>
 * Requests carry the batch of the partial recording that must be executed before they can run; the
 * consumer commits its source right after requesting it, so the source is always on the GPU by then.
 */
public final class SceneBlurSlot {
    private static final Logger LOGGER = MusicHud.getLogger(SceneBlurSlot.class);
    private static final Object ACTIVE_LOCK = new Object();
    private static final List<SceneBlurSlot> ACTIVE = new ArrayList<>();

    private final Object requestLock = new Object();
    private final List<Request> requests = new ArrayList<>();

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
            int batch = MidFrameCommit.currentBatch();
            int last = requests.size() - 1;
            Request created = new Request(image, u0, v0, u1, v1, outWidth, outHeight, sigma, batch);
            if (last >= 0 && requests.get(last).batch == batch) {
                requests.get(last).image.unref();
                requests.set(last, created);
            } else {
                requests.add(created);
            }
        }
    }

    public @Nullable Image image() {
        return disabled ? null : published.image();
    }

    /** True when at least one registered slot has a queued blur. */
    public static boolean hasPending() {
        synchronized (ACTIVE_LOCK) {
            for (SceneBlurSlot slot : ACTIVE) {
                synchronized (slot.requestLock) {
                    if (!slot.requests.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Defers a release to a later render frame, when no in-flight recording can still use it. */
    public static void retire(Runnable release) {
        SceneRetirement.retire(release);
    }

    private void discardPending() {
        synchronized (requestLock) {
            for (Request request : requests) {
                request.image.unref();
            }
            requests.clear();
        }
    }

    /** Render thread: advances the retirement clock once the frame's recordings are all submitted. */
    public static void endFrame() {
        SceneRetirement.endFrame();
    }

    /** Render thread: executes the blurs requested up to and including {@code maxBatch}. */
    public static void runAll(SceneGpuContext gpu, int maxBatch) {
        List<SceneBlurSlot> slots;
        synchronized (ACTIVE_LOCK) {
            if (ACTIVE.isEmpty()) {
                return;
            }
            slots = new ArrayList<>(ACTIVE);
        }
        for (SceneBlurSlot slot : slots) {
            slot.run(gpu, maxBatch);
        }
    }

    private void run(SceneGpuContext gpu, int maxBatch) {
        if (disabled) {
            discardPending();
            return;
        }
        Request request = null;
        synchronized (requestLock) {
            for (Iterator<Request> iterator = requests.iterator(); iterator.hasNext(); ) {
                Request candidate = iterator.next();
                if (candidate.batch > maxBatch) {
                    continue;
                }
                // Keep only the newest runnable request: it carries the current sigma, and the
                // source texture already holds this frame's content.
                if (request != null) {
                    request.image.unref();
                }
                request = candidate;
                iterator.remove();
            }
        }
        if (request == null) {
            return;
        }
        icyllis.arc3d.engine.Image image = request.image;
        float u0 = request.u0;
        float v0 = request.v0;
        float u1 = request.u1;
        float v1 = request.v1;
        int outWidth = request.outWidth;
        int outHeight = request.outHeight;
        float sigma = request.sigma;
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
            published.publish(pendingBlur.target());
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
            SceneRetirement.retire(previous::close);
        }
        return wrapped;
    }

    private Pipeline usePipeline(SceneGpuContext gpu, int width, int height, float sigma) {
        // The pipeline survives level changes: SceneBlur keeps its published result and only
        // rebuilds the pyramid, so the image consumers hold stays valid.
        Pipeline current = pipeline;
        if (current != null && current.width == width && current.height == height && current.gpu == gpu) {
            current.level = SceneBlur.levelFor(width, height, sigma);
            return current;
        }
        Pipeline created = new Pipeline(gpu, width, height, SceneBlur.levelFor(width, height, sigma));
        pipeline = created;
        if (current != null) {
            SceneRetirement.retire(current::release);
        }
        return created;
    }

    private void releaseNow() {
        discardPending();
        InteropSource previousSource = source;
        source = null;
        sourceKey = null;
        if (previousSource != null) {
            SceneRetirement.retire(previousSource::close);
        }
        Pipeline previousPipeline = pipeline;
        pipeline = null;
        if (previousPipeline != null) {
            SceneRetirement.retire(previousPipeline::release);
        }
        published.publish(null);
        published.release();
    }

    private static final class Pipeline {
        final SceneGpuContext gpu;
        final int width;
        final int height;
        int level;
        final SceneBlur blur = new SceneBlur(SceneTextureFormat.RGBA8);
        final SceneBlurCrop crop = new SceneBlurCrop(SceneTextureFormat.RGBA8);

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

    private record Request(icyllis.arc3d.engine.Image image,
                           float u0, float v0, float u1, float v1,
                           int outWidth, int outHeight, float sigma, int batch) {
    }
}
