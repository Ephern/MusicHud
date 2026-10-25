package indi.etern.musichud.client.ui.render.warp;

/** Trigonometric warp envelope over one background warp cycle. */
public final class ProceduralWarpTiming {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private ProceduralWarpTiming() {
    }

    public static float envelope(float phase) {
        float p = Math.clamp(phase, 0f, 1f);
        return .5f - .5f * (float) Math.cos(TWO_PI * p);
    }

    public static float ease(float progress) {
        float p = Math.clamp(progress, 0f, 1f);
        return p * p * (3f - 2f * p);
    }
}
