package indi.etern.musichud.client.ui.render.text;

/**
 * Horizontal alpha mask over the left and right edges of a text box, in text-local (unscaled font)
 * coordinates so the per-vertex writers can sample it per glyph corner. {@code strength} scales the
 * mask depth, so an edge fades without any geometry change.
 */
public final class TextEdgeFade {
    public static final TextEdgeFade NONE = new TextEdgeFade(0f, 0f, 0f, 0f, 0f, 0f);

    private final float leftStart;
    private final float leftEnd;
    private final float rightStart;
    private final float rightEnd;
    private final float leftStrength;
    private final float rightStrength;

    private TextEdgeFade(float leftStart, float leftEnd, float rightStart, float rightEnd,
                         float leftStrength, float rightStrength) {
        this.leftStart = leftStart;
        this.leftEnd = leftEnd;
        this.rightStart = rightStart;
        this.rightEnd = rightEnd;
        this.leftStrength = leftStrength;
        this.rightStrength = rightStrength;
    }

    /**
     * Builds a mask in the local space of a single text draw.
     *
     * @param drawOriginGuiX gui x that local x = 0 maps to (differs for the marquee wrap copy)
     * @param scale          local to gui scale of this draw
     */
    public static TextEdgeFade between(float boxLeftGui, float boxRightGui,
                                       float leftBandGui, float rightBandGui,
                                       float leftStrength, float rightStrength,
                                       float drawOriginGuiX, float scale) {
        if (scale <= 0f || !(boxRightGui > boxLeftGui)
                || (leftStrength <= 0f && rightStrength <= 0f)) {
            return NONE;
        }
        float invScale = 1f / scale;
        float localLeft = (boxLeftGui - drawOriginGuiX) * invScale;
        float localRight = (boxRightGui - drawOriginGuiX) * invScale;
        return new TextEdgeFade(
                localLeft,
                localLeft + Math.max(0f, leftBandGui) * invScale,
                localRight - Math.max(0f, rightBandGui) * invScale,
                localRight,
                clamp01(leftStrength),
                clamp01(rightStrength));
    }

    /** @return alpha multiplier in [0,1] for a glyph vertex at {@code localX}. */
    public float factorAt(float localX) {
        float factor = 1f;
        if (leftStrength > 0f) {
            // 1 at the box interior, 0 at the box left edge
            factor *= 1f - leftStrength * (1f - ramp(localX, leftStart, leftEnd));
        }
        if (rightStrength > 0f) {
            // 1 at the box interior, 0 at the box right edge
            factor *= 1f - rightStrength * ramp(localX, rightStart, rightEnd);
        }
        return factor;
    }

    /** Smooth 0..1 ramp; input outside [0,1] is clamped. */
    public static float smoothstep(float t) {
        float clamped = clamp01(t);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static float ramp(float x, float from, float to) {
        if (to <= from) {
            return x >= to ? 1f : 0f;
        }
        return smoothstep((x - from) / (to - from));
    }

    private static float clamp01(float value) {
        if (value <= 0f) return 0f;
        return Math.min(value, 1f);
    }
}
