package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.presentation.TrackArtworkStore;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneGpuContextImpl;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneWindowEnvironment;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ICommonEventService;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

public final class RenderRuntime {
    private static final Logger LOGGER = MusicHud.getLogger(RenderRuntime.class);
    private static final long STATS_LOG_INTERVAL_MS = 5_000;
    private static final int MAX_PRECOMPILE_ATTEMPTS = 8;
    private static final long PRECOMPILE_RETRY_INTERVAL_MS = 500L;
    private static final long PRECOMPILE_NOT_READY_DEADLINE_MS = 60_000L;

    private static volatile RenderRuntime instance;
    private final SceneRenderContextImpl context = new SceneRenderContextImpl();
    private SceneGpuContext gpu;
    private boolean lifecycleHookRegistered;
    private boolean debugOptionsInitialized;
    private boolean lastForceGL32Path;
    private boolean lastForceRgba8Fallback;
    private boolean gpuRecreatePending;
    private boolean logRenderStats;
    private long lastStatsLogMillis;
    private boolean scenePipelinesPrecompiled;
    private boolean precompileAbandoned;
    private int precompileFailures;
    private long nextPrecompileAttemptMillis;
    private long precompileNotReadyDeadlineMillis;

    private RenderRuntime() {
    }

    public static RenderRuntime getInstance() {
        if (instance == null) {
            synchronized (RenderRuntime.class) {
                if (instance == null) {
                    instance = new RenderRuntime();
                }
            }
        }
        return instance;
    }

    public void renderFrame(SceneGraphics graphics, RenderScene scene) {
        pollDebugTargetOptions();
        RenderStats.beginFrame();
        if (logRenderStats) {
            SceneWindowEnvironment.sample();
        }
        long startNanos = System.nanoTime();
        try {
            context.renderFrame(graphics, scene);
        } finally {
            RenderStats.addSceneNanos(System.nanoTime() - startNanos);
        }
        // After the frame: the crossfades have dropped whatever they no longer sample, so retired
        // artwork can be released without destroying a texture that is still being blended.
        TrackArtworkStore.getInstance().sweep();
        logRenderStatsIfEnabled();
    }

    public SceneRenderContext context() {
        return context;
    }

    public SceneGpuContext gpu() {
        RenderSystem.assertOnRenderThread();
        if (gpu == null) {
            gpu = new SceneGpuContextImpl();
            if (!lifecycleHookRegistered) {
                lifecycleHookRegistered = true;
                ICommonEventService.getInstance().registerCommonLifecycleStopping(this::close);
            }
        }
        return gpu;
    }

    private void pollDebugTargetOptions() {
        ClientConfig config;
        try {
            config = ClientConfig.getInstance();
        } catch (RuntimeException notReady) {
            return;
        }
        boolean forceGL32Path = config.getForceGL32Path();
        boolean forceRgba8Fallback = config.getForceRgba8Fallback();
        boolean logRenderStatsNow = config.getDebugLogRenderStats();
        if (logRenderStatsNow && !logRenderStats) {
            // Start every enabled session with a clean window, so the reported max always covers
            // exactly the last 5 seconds instead of everything since the game started.
            RenderStats.resetTimings();
            lastStatsLogMillis = System.currentTimeMillis();
        }
        logRenderStats = logRenderStatsNow;
        if (!debugOptionsInitialized) {
            debugOptionsInitialized = true;
            lastForceGL32Path = forceGL32Path;
            lastForceRgba8Fallback = forceRgba8Fallback;
            return;
        }
        if (forceGL32Path != lastForceGL32Path
                || forceRgba8Fallback != lastForceRgba8Fallback) {
            lastForceGL32Path = forceGL32Path;
            lastForceRgba8Fallback = forceRgba8Fallback;
            gpuRecreatePending = true;
        }
    }

    void processPendingGpuRecreate() {
        if (!gpuRecreatePending) return;
        gpuRecreatePending = false;
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
    }

    /** Advances the offscreen uniform rings once per recorded scene frame. */
    void endSceneFrame() {
        if (gpu != null) {
            gpu.endFrame();
        }
    }

    /**
     * Compiles every scene pipeline (and creates the GPU context) ahead of the first rendered
     * background. Doing this lazily stalls for ~100-200 ms once the first song starts.
     * <p>
     * Rate limited and idempotent: it is called from a per-tick hook until {@link
     * #isPipelinePrecompileSettled()}. Waiting for the shader sources does not count as a failure,
     * but it is bounded by a deadline so a genuinely missing shader cannot poll forever; a real
     * compilation failure is retried with a backoff and then abandoned with a single warning.
     */
    public void precompileScenePipelines() {
        if (scenePipelinesPrecompiled || precompileAbandoned) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        long now = System.currentTimeMillis();
        if (now < nextPrecompileAttemptMillis) {
            return;
        }

        SceneGpuContext context = gpu;
        if (context == null) {
            try {
                context = gpu();
            } catch (RuntimeException | LinkageError notInitialised) {
                // The render device does not exist yet: a startup race, not a failure.
            }
        }

        SceneGpuContext.PrecompileResult result;
        long startNanos = System.nanoTime();
        if (context == null) {
            result = SceneGpuContext.PrecompileResult.NOT_READY;
        } else {
            try {
                result = context.precompilePipelines();
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.debug("While precompiling scene pipelines", failure);
                result = SceneGpuContext.PrecompileResult.FAILED;
            }
        }

        switch (result) {
            case DONE -> {
                scenePipelinesPrecompiled = true;
                LOGGER.debug("Precompiled scene pipelines in {} ms",
                        (System.nanoTime() - startNanos) / 1_000_000L);
            }
            case NOT_READY -> {
                if (precompileNotReadyDeadlineMillis == 0L) {
                    precompileNotReadyDeadlineMillis = now + PRECOMPILE_NOT_READY_DEADLINE_MS;
                } else if (now >= precompileNotReadyDeadlineMillis) {
                    precompileAbandoned = true;
                    LOGGER.warn("Scene shaders never became available; pipelines will compile lazily");
                }
                nextPrecompileAttemptMillis = now + PRECOMPILE_RETRY_INTERVAL_MS;
            }
            case FAILED -> {
                precompileFailures++;
                if (precompileFailures >= MAX_PRECOMPILE_ATTEMPTS) {
                    precompileAbandoned = true;
                    LOGGER.warn("Gave up precompiling scene pipelines after {} attempts",
                            precompileFailures);
                } else {
                    nextPrecompileAttemptMillis = now + PRECOMPILE_RETRY_INTERVAL_MS;
                }
            }
        }
    }

    public boolean isPipelinePrecompileSettled() {
        return scenePipelinesPrecompiled || precompileAbandoned;
    }

    private void logRenderStatsIfEnabled() {
        if (!logRenderStats) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastStatsLogMillis < STATS_LOG_INTERVAL_MS) {
            return;
        }
        lastStatsLogMillis = now;
        RenderStats.Snapshot stats = RenderStats.snapshot();
        LOGGER.info("[RenderStats] passes={} targets(+{} -{}) live={} liveMiB={} uniformBytes={} meshBytes={}"
                        + " | scene={}us/{} max={}us offscreen={}us/{} max={}us uniforms={}us/{} max={}us{}",
                stats.passes(), stats.targetsCreated(), stats.targetsReleased(), stats.liveTargets(),
                String.format(Locale.ROOT, "%.1f", stats.liveTargetBytes() / 1048576.0),
                stats.uniformBytes(), stats.meshBytes(),
                RenderStats.Snapshot.averageMicros(stats.sceneNanos(), stats.sceneCalls()), stats.sceneCalls(),
                RenderStats.Snapshot.maxMicros(stats.sceneMaxNanos()),
                RenderStats.Snapshot.averageMicros(stats.offscreenNanos(), stats.offscreenCalls()), stats.offscreenCalls(),
                RenderStats.Snapshot.maxMicros(stats.offscreenMaxNanos()),
                RenderStats.Snapshot.averageMicros(stats.uniformNanos(), stats.uniformCalls()), stats.uniformCalls(),
                RenderStats.Snapshot.maxMicros(stats.uniformMaxNanos()),
                RenderStats.phaseSpikes(2_000_000L) + RenderStats.windowAllocations()
                        + SceneWindowEnvironment.describe());
        RenderStats.resetTimings();
        SceneWindowEnvironment.beginWindow();
    }

    public void close() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
    }
}
