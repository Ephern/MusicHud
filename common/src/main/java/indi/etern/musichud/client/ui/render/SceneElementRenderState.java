package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.utils.ui.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record SceneElementRenderState(
        @NonNull ScenePipeline pipeline,
        @NonNull SceneTextureSetup textureSetup,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        @Nullable ScreenRectangle bounds,
        SceneUniform[] uniforms
) {
    public SceneElementRenderState(@NonNull ScenePipeline pipeline,
                          @NonNull SceneTextureSetup textureSetup,
                          @NonNull Matrix3x2f pose,
                          @NonNull SceneStyle hudStyle,
                          SceneUniform... uniforms) {
        this(pipeline, textureSetup, pose, hudStyle.getWidth(), hudStyle.getHeight(),
                UniformDataUtils.getBounds(-hudStyle.getWidth() / 2f, -hudStyle.getHeight() / 2f, hudStyle.getWidth() / 2f, hudStyle.getHeight() / 2f, pose),
                uniforms);
    }
}
