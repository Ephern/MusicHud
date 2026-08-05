package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.TimeInterpolator;

public enum Easing implements TimeInterpolator{
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
            return (float) Math.sin((t * Math.PI) / 2);
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
    },
    EASE_OUT_BACK_1 {
        @Override
        public float getInterpolation(float t) {
            float c1 = 3.8f;
            float c3 = c1 + 1;
            return (float) (1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
        }
    }
}
