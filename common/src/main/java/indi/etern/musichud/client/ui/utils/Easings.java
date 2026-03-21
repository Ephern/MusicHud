package indi.etern.musichud.client.ui.utils;

import icyllis.modernui.animation.TimeInterpolator;

public enum Easings implements TimeInterpolator{
    EASE_OUT_QUINT {
        @Override
        public float getInterpolation(float t) {
            return 1 - (float) Math.pow(1 - t, 5);
        }
    },
    EASE_IN_QUINT {
        @Override
        public float getInterpolation(float t) {
            return (float) Math.pow(t, 5);
        }
    },
    EASE_IN_OUT_QUINT {
        @Override
        public float getInterpolation(float t) {
            return t < 0.5 ? 16 * t * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;
        }
    },
    EASE_OUT_QUAD {
        @Override
        public float getInterpolation(float t) {
            return 1 - (1 - t) * (1 - t);
        }
    },
    EASE_IN_QUAD {
        @Override
        public float getInterpolation(float t) {
            return t * t;
        }
    },
    EASE_IN_OUT_QUAD {
        @Override
        public float getInterpolation(float t) {
            return t < 0.5 ? 2 * t * t : (float) (1 - Math.pow(-2 * t + 2, 2) / 2);
        }
    },
    EASE_OUT_SINE {
        @Override
        public float getInterpolation(float t) {
            return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
        }
    },
    EASE_IN_SINE {
        @Override
        public float getInterpolation(float t) {
            return (float) (1 - Math.cos((t * Math.PI) / 2));
        }
    },
    EASE_IN_OUT_SINE {
        @Override
        public float getInterpolation(float t) {
            return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
        }
    }
}
