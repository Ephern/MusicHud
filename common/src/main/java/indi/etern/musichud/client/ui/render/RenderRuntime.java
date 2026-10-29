package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneGpuContextImpl;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ICommonEventService;

public final class RenderRuntime {
    private static volatile RenderRuntime instance;
    private final SceneRenderContextImpl context = new SceneRenderContextImpl();
    private SceneGpuContext gpu;
    private boolean lifecycleHookRegistered;
    private boolean debugOptionsInitialized;
    private boolean lastForceGL32Path;
    private boolean lastForceRgba8Fallback;
    private boolean gpuRecreatePending;

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
        context.renderFrame(graphics, scene);
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

    public void close() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
    }
}
