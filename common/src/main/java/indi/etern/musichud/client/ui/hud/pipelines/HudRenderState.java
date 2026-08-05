package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.utils.ui.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record HudRenderState(
        @NonNull HudShaderProgram pipeline,
        @Nullable Integer[] textures,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        @Nullable ScreenRectangle bounds,
        HudUniform[] uniforms
) {

    public HudRenderState(@NonNull HudShaderProgram pipeline,
                          @Nullable Integer[] textures,
                          @NonNull Matrix3x2f pose,
                          @NonNull Layout layout,
                          HudUniform... uniforms) {
        this(pipeline, textures, pose, layout.getWidth(), layout.getHeight(),
                UniformDataUtils.getBounds(-layout.getWidth() / 2f, -layout.getHeight() / 2f, layout.getWidth() / 2f, layout.getHeight() / 2f, pose), uniforms);
    }
}
