package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

public interface SceneGpuContext extends AutoCloseable {
    SceneGpuTexture createRenderTarget(String label, int width, int height,
                                       SceneTextureFormat format);

    SceneSampler sampler(SceneSamplerState state);

    /** {@code targetFormat} must be {@link SceneGpuTexture#actualFormat()}. */
    ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat);

    SceneMesh createMesh(String label, int vertexBytes, short[] indices);

    void updateMesh(SceneMesh mesh, ByteBuffer vertices);

    void draw(SceneDraw draw);

    void drawIndexed(SceneIndexedDraw draw);

    void clear(SceneGpuTexture target, int argb);

    void release(SceneGpuTexture texture);

    void release(SceneMesh mesh);

    @Nullable
    MainColorSource mainColorSource();

    @Override
    void close();

    record MainColorSource(SceneTextureRef texture, int width, int height, int guiScale) {
    }
}
