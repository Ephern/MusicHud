package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContextImpl;
import indi.etern.musichud.interfaces.ICommonEventService;

public final class RenderRuntime {
    private static volatile RenderRuntime instance;
    private final SceneRenderContextImpl context = new SceneRenderContextImpl();
    private SceneGpuContext gpu;

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
        context.renderFrame(graphics, scene);
    }

    public SceneRenderContext context() {
        return context;
    }

    public SceneGpuContext gpu() {
        RenderSystem.assertOnRenderThread();
        if (gpu == null) {
            gpu = new SceneGpuContextImpl();
            ICommonEventService.getInstance().registerCommonLifecycleStopping(this::close);
        }
        return gpu;
    }

    public void close() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
    }
}
