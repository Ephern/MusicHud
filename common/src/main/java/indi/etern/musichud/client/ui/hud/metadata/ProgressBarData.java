package indi.etern.musichud.client.ui.hud.metadata;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.utils.ui.UniformDataUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

public class ProgressBarData implements HudUniform {
    public static final int UBO_SIZE = new Std140SizeCalculator().putVec3().putVec4().putVec4().putVec4().align(16).get();
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
    public void write(Std140Builder builder) {
        builder.putVec3(gradientLength, gradientRightOffset, transitionBorderRate)
               .putVec4(UniformDataUtils.colorToVector(playedColor))
               .putVec4(UniformDataUtils.colorToVector(currentColor))
               .putVec4(UniformDataUtils.colorToVector(backgroundColor));
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