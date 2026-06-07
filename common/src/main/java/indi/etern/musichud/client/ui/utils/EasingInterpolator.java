package indi.etern.musichud.client.ui.utils;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.annotation.NonNull;

public class EasingInterpolator implements TimeInterpolator {
    private final Easing easing;

    public EasingInterpolator(@NonNull Easing easing) {
        this.easing = easing;
    }

    @Override
    public float getInterpolation(float input) {
        return easing.getInterpolation(input);
    }

    // 便捷的静态工厂方法
    public static TimeInterpolator of(Easing easing) {
        return new EasingInterpolator(easing);
    }
}