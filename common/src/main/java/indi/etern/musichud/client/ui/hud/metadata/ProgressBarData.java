package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.Std140BufferWriter;
import indi.etern.musichud.client.ui.utils.ui.UniformDataUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

public class ProgressBarData implements HudUniform {
    public static final int UBO_SIZE = new Std140BufferWriter.Calculator().putVec3().putVec4().putVec4().putVec4().align(16).get();
    public final int playedColor;
    public final int currentColor;
    public final int backgroundColor;
    public final float gradientLength;
    public final float gradientRightOffset;
    public final float transitionBorderRate;
    @Getter
    @Setter
    private Layout layout;

    public ProgressBarData(Layout layout, int playedColor, int currentColor, int backgroundColor, float gradientLength, float gradientRightOffset, float transitionBorderRate) {
        this.layout = layout;
        this.playedColor = playedColor;
        this.currentColor = currentColor;
        this.backgroundColor = backgroundColor;
        this.gradientLength = gradientLength;
        this.gradientRightOffset = gradientRightOffset;
        this.transitionBorderRate = transitionBorderRate;
    }


    @Override
    public String getUBOName() {
        return "MHProgressStyle";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140BufferWriter builder) {
        builder.putVec3(gradientLength, gradientRightOffset, transitionBorderRate);
        org.joml.Vector4f v = UniformDataUtils.colorToVector(playedColor);
        builder.putVec4(v.x, v.y, v.z, v.w);
        v = UniformDataUtils.colorToVector(currentColor);
        builder.putVec4(v.x, v.y, v.z, v.w);
        v = UniformDataUtils.colorToVector(backgroundColor);
        builder.putVec4(v.x, v.y, v.z, v.w);
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        return lastBuffered instanceof ProgressBarData progressBarData
                && Objects.equals(playedColor, progressBarData.playedColor)
                && Objects.equals(currentColor, progressBarData.currentColor)
                && Objects.equals(backgroundColor, progressBarData.backgroundColor)
                && Objects.equals(gradientLength, progressBarData.gradientLength)
                && Objects.equals(gradientRightOffset, progressBarData.gradientRightOffset)
                && Objects.equals(transitionBorderRate, progressBarData.transitionBorderRate)
                && layout.shouldUseBuffer(progressBarData.layout);
    }
}