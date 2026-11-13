package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralWarpCycleTest {
    @Test
    void phaseAndIndexFollowTheTenSecondCycle() {
        float start = 3.25f;
        assertEquals(0f, ProceduralWarpCycle.phase(start, start), 1e-6f);
        assertEquals(.5f, ProceduralWarpCycle.phase(start + 5f, start), 1e-5f);
        assertTrue(ProceduralWarpCycle.phase(start + 10f, start) < 1e-5f);
        assertEquals(0, ProceduralWarpCycle.index(start + 9.99f, start));
        assertEquals(1, ProceduralWarpCycle.index(start + 10f, start));
        assertEquals(.25f, ProceduralWarpCycle.phase(start + 12.5f, start), 1e-5f);
    }
}
