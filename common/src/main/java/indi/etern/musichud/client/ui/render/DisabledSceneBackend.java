package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneIndexedDraw;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import indi.etern.musichud.client.ui.render.gpu.ScenePipelineKey;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Safe fallback when a platform backend is present but cannot be created. Scene effects disappear;
 * the HUD still draws via {@link SceneGraphics} where possible instead of taking the game down.
 */
final class DisabledSceneBackend implements SceneBackend {
    private final DisabledGpu gpu = new DisabledGpu();
    private final DisabledRenderer renderer = new DisabledRenderer();

    @Override
    public SceneGpuContext gpu() {
        return gpu;
    }

    @Override
    public ScenePipelineRenderer pipelineRenderer() {
        return renderer;
    }

    @Override
    public void close() {
    }

    private static final class DisabledRenderer implements ScenePipelineRenderer {
        @Override
        public void submit(RenderLayer owner, SceneElementRenderState state) {
            SceneRenderContext context = SceneRenderContextImpl.getCurrent();
            if (context == null) {
                return;
            }
            SceneGraphics graphics = context.graphics();
            if (graphics == null) {
                return;
            }
            float left = graphics.guiWidth() / 2f + state.pose().m20;
            float top = graphics.guiHeight() / 2f + state.pose().m21;
            int x0 = (int) (left - state.width() / 2f);
            int y0 = (int) (top - state.height() / 2f);
            int x1 = (int) (left + state.width() / 2f);
            int y1 = (int) (top + state.height() / 2f);
            graphics.fill(x0, y0, x1, y1, 0x33FFFFFF);
        }
    }

    private static final class DisabledGpu implements SceneGpuContext {
        @Override
        public SceneGpuTexture createRenderTarget(String label, int width, int height,
                                                  SceneTextureFormat format) {
            throw disabled();
        }

        @Override
        public SceneGpuTexture createNativeRenderTarget(String label, int width, int height,
                                                        SceneTextureFormat format) {
            throw disabled();
        }

        @Override
        public ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat) {
            throw disabled();
        }

        @Override
        public SceneMesh createMesh(String label, int vertexBytes, short[] indices) {
            throw disabled();
        }

        @Override
        public void updateMesh(SceneMesh mesh, ByteBuffer vertices) {
            throw disabled();
        }

        @Override
        public void draw(indi.etern.musichud.client.ui.render.gpu.SceneDraw draw) {
            throw disabled();
        }

        @Override
        public void drawIndexed(SceneIndexedDraw draw) {
            throw disabled();
        }

        @Override
        public void clear(SceneGpuTexture target, int argb) {
        }

        @Override
        public PrecompileResult precompilePipelines() {
            return PrecompileResult.DONE;
        }

        @Override
        public void release(SceneGpuTexture texture) {
        }

        @Override
        public void release(SceneMesh mesh) {
        }

        @Override
        public @Nullable MainColorSource mainColorSource() {
            return null;
        }

        @Override
        public void close() {
        }

        private static UnsupportedOperationException disabled() {
            return new UnsupportedOperationException("Scene GPU backend is disabled");
        }
    }
}
