package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarpParameterSpecTest {
    @Test
    void denominatorMatchesFloatOptionStepConvention() {
        assertEquals(100, new WarpParameterSpec(
                WarpParameter.AMPLITUDE, 0, 1, .01f, .5f).denominator());
        assertEquals(10, new WarpParameterSpec(
                WarpParameter.DECAY, 0, 4, .1f, 1).denominator());
        assertEquals(1, new WarpParameterSpec(
                WarpParameter.ANGLE, -90, 90, 1, 0).denominator());
    }
}
