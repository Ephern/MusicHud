package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

public interface SceneGpuContext extends AutoCloseable {
    SceneGpuTexture createRenderTarget(String label, int width, int height,
                                       SceneTextureFormat format);

    /**
     * {@code targetFormat} is only a hint: a pipeline is target-format agnostic (blend state and
     * vertex format do not depend on it), so implementations may share one pipeline across formats.
     */
    ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat);

    SceneMesh createMesh(String label, int vertexBytes, short[] indices);

    void updateMesh(SceneMesh mesh, ByteBuffer vertices);

    void draw(SceneDraw draw);

    void drawIndexed(SceneIndexedDraw draw);

    void clear(SceneGpuTexture target, int argb);

    /** Advances per-frame upload rings; implementations may no-op. */
    default void endFrame() {
    }

    /**
     * Compiles every scene pipeline up front. GLSL compilation of the whole chain costs ~200 ms on
     * a cold driver cache, which otherwise stalls the first rendered background frame.
     * <p>
     * The result is tri-state on purpose: waiting for the shader sources is a normal startup race
     * (silent retry), while a compilation failure is a real error (bounded retry + one warning).
     */
    default PrecompileResult precompilePipelines() {
        return PrecompileResult.DONE;
    }

    enum PrecompileResult {
        /** Every pipeline compiled; no further attempts are needed. */
        DONE,
        /** Shader sources or the render device are not available yet; retry silently. */
        NOT_READY,
        /** Shader sources were available but compilation failed; retry with backoff. */
        FAILED
    }

    void release(SceneGpuTexture texture);

    void release(SceneMesh mesh);

    @Nullable
    MainColorSource mainColorSource();

    @Override
    void close();

    record MainColorSource(SceneTextureRef texture, int width, int height, int guiScale) {
    }
}
