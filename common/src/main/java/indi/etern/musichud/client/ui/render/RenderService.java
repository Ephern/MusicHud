package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContextImpl;
import indi.etern.musichud.interfaces.ICommonEventService;

public final class RenderService {
    private static volatile RenderService instance;
    private final SceneRenderContextImpl context = new SceneRenderContextImpl();
    private SceneGpuContext gpu;

    private RenderService() {
    }

    public static RenderService getInstance() {
        if (instance == null) {
            synchronized (RenderService.class) {
                if (instance == null) {
                    instance = new RenderService();
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
