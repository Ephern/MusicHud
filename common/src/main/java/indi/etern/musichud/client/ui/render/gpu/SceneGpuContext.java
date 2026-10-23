package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;

import java.nio.ByteBuffer;

public interface SceneGpuContext extends AutoCloseable {
    SceneGpuTexture createRenderTarget(String label, int width, int height,
                                       SceneTextureFormat format, SceneSamplerState sampler);

    SceneSampler sampler(SceneSamplerState state);

    ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat);

    SceneMesh createMesh(String label, int vertexBytes, short[] indices);

    void updateMesh(SceneMesh mesh, ByteBuffer vertices);

    void draw(SceneDraw draw);

    void drawIndexed(SceneIndexedDraw draw);

    void clear(SceneGpuTexture target, int argb);

    byte[] readPixels(SceneGpuTexture target);

    void release(SceneGpuTexture texture);

    void release(SceneMesh mesh);

    @Override
    void close();
}
