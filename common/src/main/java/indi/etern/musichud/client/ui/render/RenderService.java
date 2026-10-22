package indi.etern.musichud.client.ui.render;

public final class RenderService {
    private static volatile RenderService instance;
    private final SceneRenderContextImpl context = new SceneRenderContextImpl();

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
}
