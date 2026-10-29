package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SceneCoverLayers {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverLayers.class);
    private static final int MAX_LONG_EDGE = FluidBackgroundSettings.COVER_TARGET_MAX_LONG_EDGE;

    public static final float COVER_LONG_EDGE_SPAN = (float) (2.0 * Math.sqrt(2.0));
    public static final float COVER_BASE_HALF_SPAN = COVER_LONG_EDGE_SPAN / 2f;
    public static final float SECOND_LAYER_LONG_EDGE_SPAN = 1.2f;
    public static final float SECOND_LAYER_SCALE =
            SECOND_LAYER_LONG_EDGE_SPAN / COVER_LONG_EDGE_SPAN;
    public static final float SECOND_LAYER_SPEED_RATIO =
            (5f / 8f) * (float) Math.sqrt(1.1);
    public static final float SECOND_LAYER_PHASE = (float) Math.PI;
    public static final float THIRD_LAYER_LONG_EDGE_SPAN = 1.4f;
    public static final float THIRD_LAYER_SCALE =
            THIRD_LAYER_LONG_EDGE_SPAN / COVER_LONG_EDGE_SPAN;
    public static final float THIRD_LAYER_SPEED_RATIO = 3f / 4f;
    public static final float THIRD_LAYER_ORBIT_SPEED_RATIO =
            (3f / 8f) * (2f / (float) Math.sqrt(5.0));
    public static final float THIRD_LAYER_ORBIT_RADIUS = 1f;

    private SceneGpuContext owner;
    private int width;
    private int height;
    private SceneGpuTexture target;

    public @Nullable SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture cover,
                                           CoverUniform uniform,
                                           int canvasWidth, int canvasHeight) {
        if (gpu == null || cover == null || uniform == null
                || canvasWidth < 1 || canvasHeight < 1) {
            return null;
        }
        int longEdge = Math.clamp(Math.max(canvasWidth, canvasHeight),
                1, MAX_LONG_EDGE);
        int width;
        int height;
        if (canvasWidth >= canvasHeight) {
            width = longEdge;
            height = Math.max(1, Math.round((float) longEdge * canvasHeight / canvasWidth));
        } else {
            height = longEdge;
            width = Math.max(1, Math.round((float) longEdge * canvasWidth / canvasHeight));
        }
        if (target == null || owner != gpu || this.width != width || this.height != height) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud cover layers", width, height,
                        SceneTextureFormat.RGBA16F);
                owner = gpu;
                this.width = width;
                this.height = height;
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the cover layers target", failure);
                release(gpu);
                return null;
            }
        }
        try {
            gpu.clear(target, 0);
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_COVER_LAYERS, target.actualFormat()),
                    List.of(new SceneSamplerBinding("Sampler0", cover,
                            gpu.sampler(SceneSamplerState.linearClamp()))),
                    List.of(new SceneUniformBinding(CoverUniform.UBO_NAME, uniform))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While composing the cover layers", failure);
            release(gpu);
            return null;
        }
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
    }

    public record CoverUniform(float speedRadiansPerSecond, float opacity, float timeSeconds,
                               float driftX, float driftY, float thirdCenterX, float thirdCenterY,
                               float halfSpanX, float halfSpanY, float canvasAspect,
                               boolean enabled) implements SceneUniform {
        private static final String UBO_NAME = "MHCover";
        private static final int UBO_SIZE = Std140Sizes.calc()
                .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();

        public static CoverUniform create(float speedRadiansPerSecond, float opacity,
                                          float timeSeconds, float driftX, float driftY,
                                          float thirdCenterX, float thirdCenterY,
                                          float canvasAspect,
                                          int coverWidth, int coverHeight) {
            float aspect = Math.max(1, coverWidth) / (float) Math.max(1, coverHeight);
            float base = COVER_BASE_HALF_SPAN;
            float halfSpanX;
            float halfSpanY;
            if (aspect >= 1f) {
                halfSpanX = base;
                halfSpanY = base / aspect;
            } else {
                halfSpanX = base * aspect;
                halfSpanY = base;
            }
            return new CoverUniform(speedRadiansPerSecond, opacity, timeSeconds, driftX, driftY,
                    thirdCenterX, thirdCenterY, halfSpanX, halfSpanY,
                    canvasAspect, opacity > 1e-4f);
        }

        @Override
        public String getUBOName() {
            return UBO_NAME;
        }

        @Override
        public int getUBOSize() {
            return UBO_SIZE;
        }

        @Override
        public void write(Std140Writer writer) {
            float angle1 = speedRadiansPerSecond * timeSeconds;
            float angle2 = angle1 * SECOND_LAYER_SPEED_RATIO + SECOND_LAYER_PHASE;
            float angle3 = angle1 * THIRD_LAYER_SPEED_RATIO;
            float enabledValue = enabled ? 1f : 0f;
            writer.putVec4(0f, 0f, (float) Math.cos(angle1), (float) Math.sin(angle1));
            writer.putVec4(halfSpanX, halfSpanY, opacity, enabledValue);
            writer.putVec4(driftX, driftY, (float) Math.cos(angle2), (float) Math.sin(angle2));
            writer.putVec4(halfSpanX * SECOND_LAYER_SCALE,
                    halfSpanY * SECOND_LAYER_SCALE, opacity, enabledValue);
            writer.putVec4(thirdCenterX, thirdCenterY,
                    (float) Math.cos(angle3), (float) Math.sin(angle3));
            writer.putVec4(halfSpanX * THIRD_LAYER_SCALE,
                    halfSpanY * THIRD_LAYER_SCALE, opacity, enabledValue);
            writer.putVec4(canvasAspect, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
