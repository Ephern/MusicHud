package indi.etern.musichud.client.ui.render.warp;

public final class ProceduralWarpCycle {
    public static final float PERIOD_SECONDS = 10f;

    private ProceduralWarpCycle() {
    }

    public static long index(float time, float startTime) {
        return (long) Math.floor((time - startTime) / PERIOD_SECONDS);
    }

    public static float phase(float time, float startTime) {
        double cycles = (time - startTime) / (double) PERIOD_SECONDS;
        float phase = (float) (cycles - Math.floor(cycles));
        return Math.clamp(phase, 0f, 1f);
    }
}
