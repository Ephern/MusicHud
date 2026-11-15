package indi.etern.musichud.client.ui.drawable;

import lombok.Getter;

/**
 * Density-independent description of a linear progress bar. Dimensions are dp and are resolved
 * to pixels by the owning view, so one style instance can back several widgets.
 */
@Getter
public final class LinearProgressStyle {

    private static final float DEFAULT_HEIGHT_DP = 4;

    private final int trackColor;
    private final int[] fillColors;
    private final float trackThicknessDp;
    private final boolean trackFillsHeight;
    private final float heightDp;
    private final float cornerRadiusDp;
    private final float gradientLengthDp;
    private final float alphaFadeIn;

    private LinearProgressStyle(Builder builder) {
        trackColor = builder.trackColor;
        fillColors = builder.fillColors;
        trackFillsHeight = builder.trackFillsHeight;
        trackThicknessDp = Math.max(0, builder.trackThicknessDp);
        float height = builder.heightDp > 0
                ? builder.heightDp
                : trackFillsHeight ? DEFAULT_HEIGHT_DP : trackThicknessDp;
        heightDp = Math.max(height, trackFillsHeight ? 0 : trackThicknessDp);
        cornerRadiusDp = builder.cornerRadiusDp;
        gradientLengthDp = Math.max(0, builder.gradientLengthDp);
        alphaFadeIn = Math.clamp(builder.alphaFadeIn, 0f, 1f);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int trackColor = 0x00000000;
        private int[] fillColors = {0xFFFFFFFF};
        private float trackThicknessDp = 4;
        private boolean trackFillsHeight = false;
        private float heightDp = -1;
        private float cornerRadiusDp = -1;
        private float gradientLengthDp = 0;
        private float alphaFadeIn = 0;

        private Builder() {
        }

        public Builder trackColor(int color) {
            trackColor = color;
            return this;
        }

        /**
         * One color for a solid fill, two or more for a gradient whose head is the last entry.
         */
        public Builder fillColors(int... colors) {
            if (colors.length == 0) {
                throw new IllegalArgumentException("fillColors must not be empty");
            }
            fillColors = colors;
            return this;
        }

        /**
         * Fixed track thickness; ignored when {@link #trackFillsHeight()} is used.
         */
        public Builder trackThicknessDp(float dp) {
            trackThicknessDp = dp;
            return this;
        }

        /**
         * Fill the whole laid-out content height instead of using a fixed thickness.
         */
        public Builder trackFillsHeight() {
            trackFillsHeight = true;
            return this;
        }

        /**
         * Layout height; defaults to the track thickness, or 4dp when the track fills the height.
         * Extra height is padded around a fixed-thickness track.
         */
        public Builder heightDp(float dp) {
            heightDp = dp;
            return this;
        }

        /**
         * Negative values mean a fully rounded (pill) cap.
         */
        public Builder cornerRadiusDp(float dp) {
            cornerRadiusDp = dp;
            return this;
        }

        /**
         * Length of the head gradient in dp; ignored for solid fills.
         */
        public Builder gradientLengthDp(float dp) {
            gradientLengthDp = dp;
            return this;
        }

        /**
         * Fraction of the range over which the fill fades in, hiding the pixel-sized fill at 0.
         */
        public Builder alphaFadeIn(float fraction) {
            alphaFadeIn = fraction;
            return this;
        }

        public LinearProgressStyle build() {
            if (!trackFillsHeight && !(trackThicknessDp > 0)) {
                throw new IllegalArgumentException(
                        "trackThicknessDp must be positive unless the track fills the height");
            }
            return new LinearProgressStyle(this);
        }
    }
}
