package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Surface;
import indi.etern.musichud.MusicHud;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Per-consumer blur pipeline. The UI thread records the blur source into an arc3d surface and hands
 * its texture over; the render thread crops and blurs it with {@link SceneBlur}, then publishes the
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

    private GLTexture pendingSource;
    private float u0;
    private float v0;
    private float u1 = 1f;
    private float v1 = 1f;
    private int outWidth;
    private int outHeight;
    private float sigma;
    private boolean pending;

    private Pipeline pipeline;
    private WrappedSceneTexture source;
    private final PublishedSceneTexture published = new PublishedSceneTexture();
    @Getter
    private volatile boolean disabled;

    /** UI thread: references the backing texture of an arc3d render target. */
    public static @Nullable GLTexture textureOf(@Nullable Surface surface) {
        if (!(surface instanceof GraniteSurface granite)) {
            return null;
        }
        var proxy = granite.getBackingTarget();
        if (proxy == null) {
            return null;
        }
        var image = proxy.getImage();
        return image instanceof GLTexture texture ? RefCnt.create(texture) : null;
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
    public void request(@Nullable GLTexture texture,
                        float u0, float v0, float u1, float v1,
                        int outWidth, int outHeight, float sigma) {
        if (texture == null || disabled || outWidth < 1 || outHeight < 1 || !(sigma > 0f)) {
            if (texture != null) {
                texture.unref();
            }
            return;
        }
        synchronized (requestLock) {
            if (pendingSource != null) {
                pendingSource.unref();
            }
            pendingSource = texture;
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
        GLTexture texture;
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
            texture = pendingSource;
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
        try {
            WrappedSceneTexture input = useSource(texture);
            if (input == null) {
                return;
            }
            Pipeline current = usePipeline(gpu, outWidth, outHeight, sigma);
            SceneGpuTexture blurSource = input;
            boolean cropped = u0 > 0f || v0 > 0f || u1 < 1f || v1 < 1f
                    || input.width() != outWidth || input.height() != outHeight;
            if (cropped) {
                SceneGpuTexture crop = current.crop.apply(gpu, input,
                        u0, v0, u1, v1, outWidth, outHeight);
                if (crop == null) {
                    return;
                }
                blurSource = crop;
            }
            SceneBlur.PendingBlur pendingBlur = current.blur.prepare(gpu,
                    blurSource.width(), blurSource.height(), sigma);
            if (pendingBlur == null || !pendingBlur.apply(gpu, blurSource)) {
                return;
            }
            SceneGpuTexture target = pendingBlur.target();
            current.result = target;
            published.publish(target);
        } catch (Throwable failure) {
            // A broken slot must not take the whole UI down; the consumer falls back to a sharp draw.
            LOGGER.error("Disabling backdrop blur after an error", failure);
            shutdown();
        }
    }

    private @Nullable WrappedSceneTexture useSource(GLTexture texture) {
        if (texture.getHandle() == 0) {
            texture.unref();
            return null;
        }
        if (!GL11.glIsTexture(texture.getHandle())) {
            LOGGER.warn("Blur source texture {} is no longer valid", texture.getHandle());
            texture.unref();
            return null;
        }
        if (source != null && source.sourceTexture() == texture) {
            texture.unref();
            return source;
        }
        WrappedSceneTexture wrapped = WrappedSceneTexture.wrap(texture);
        if (wrapped == null) {
            return null;
        }
        WrappedSceneTexture previous = source;
        source = wrapped;
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
        WrappedSceneTexture previousSource = source;
        source = null;
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
