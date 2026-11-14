package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralWarpTimingTest {
    @Test
    void envelopeFollowsARaisedCosineWithoutAPeakHold() {
        assertEquals(0f, ProceduralWarpTiming.envelope(0f), 1e-6f);
        assertEquals(.5f, ProceduralWarpTiming.envelope(.25f), 1e-6f);
        assertEquals(1f, ProceduralWarpTiming.envelope(.5f), 1e-6f);
        assertEquals(.5f, ProceduralWarpTiming.envelope(.75f), 1e-6f);
        assertEquals(0f, ProceduralWarpTiming.envelope(1f), 1e-6f);
        assertTrue(ProceduralWarpTiming.envelope(.1f) < ProceduralWarpTiming.envelope(.2f));
        assertTrue(ProceduralWarpTiming.envelope(.4f) < ProceduralWarpTiming.envelope(.5f));
        assertTrue(ProceduralWarpTiming.envelope(.8f) > ProceduralWarpTiming.envelope(.9f));
    }
}
