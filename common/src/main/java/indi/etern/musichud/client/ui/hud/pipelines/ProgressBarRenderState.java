package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.client.ui.utils.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;


public record ProgressBarRenderState(
        @NonNull HudShaderProgram pipeline,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        int leftFillColor,
        int rightFillColor,
        int backgroundColor,
        @Nullable ScreenRectangle bounds
) {

    public ProgressBarRenderState(@NonNull HudShaderProgram pipeline,
                                  @NonNull Matrix3x2f pose,
                                  float width, float height,
                                  int leftFillColor,
                                  int rightFillColor,
                                  int backgroundColor) {
        this(pipeline, pose, width, height,
                leftFillColor, rightFillColor, backgroundColor,
                UniformDataUtils.getBounds(-width / 2f, -height / 2f, width / 2f, height / 2f, pose));
    }
}