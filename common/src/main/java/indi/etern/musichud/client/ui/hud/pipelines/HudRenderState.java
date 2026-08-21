package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.utils.ui.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * Version-neutral description of a single HUD element.
 * <p>
 * {@code elementKey} identifies the logical element (e.g. {@code "background"}) so that
 * multiple elements sharing the same pipeline but carrying different uniform content are
 * kept in separate draws and get their own uniform binding.
 */
public record HudRenderState(
        @NonNull HudPipeline pipeline,
        @NonNull HudTextureSetup textureSetup,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        @Nullable ScreenRectangle bounds,
        @Nullable String elementKey,
        HudUniform[] uniforms
) {
    public HudRenderState(@NonNull HudPipeline pipeline,
                          @NonNull HudTextureSetup textureSetup,
                          @NonNull Matrix3x2f pose,
                          @NonNull Layout layout,
                          @Nullable String elementKey,
                          HudUniform... uniforms) {
        this(pipeline, textureSetup, pose, layout.getWidth(), layout.getHeight(),
                UniformDataUtils.getBounds(-layout.getWidth() / 2f, -layout.getHeight() / 2f, layout.getWidth() / 2f, layout.getHeight() / 2f, pose),
                elementKey, uniforms);
    }
}
