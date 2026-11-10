package indi.etern.musichud.client.ui.render.warp;

final class NoiseFlowField {
    private NoiseFlowField() {
    }

    static void sample(float x, float y, WarpPreset preset, float[] out) {
        float global = preset.value(WarpParameter.GLOBAL);
        float detail = preset.value(WarpParameter.DETAIL);
        float angle = radians(preset.value(WarpParameter.ANGLE));
        float perspective = preset.value(WarpParameter.PERSPECTIVE);
        float nx = x - .5f;
        float ny = y - .5f;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float u = nx * c + ny * s;
        float v = -nx * s + ny * c;
        out[0] = global * (.9f * v + .65f * perspective * u * v);
        out[1] = global * (.7f * u + .4f * perspective * (u * u - v * v));

        float scale = Math.max(.5f, preset.value(WarpParameter.SCALE));
        int octaves = Math.clamp(Math.round(preset.value(WarpParameter.OCTAVES)), 1, 5);
        float phase = preset.value(WarpParameter.PHASE);
        int seed = preset.seed();
        float z = phase * 4f;
        float h = .5f / scale;
        float right = fbm3((x + h) * scale, y * scale, z, seed, octaves);
        float left = fbm3((x - h) * scale, y * scale, z, seed, octaves);
        float up = fbm3(x * scale, (y + h) * scale, z, seed, octaves);
        float down = fbm3(x * scale, (y - h) * scale, z, seed, octaves);
        float dx = right - left;
        float dy = up - down;
        if (preset.isStream()) {
            float compress = Math.clamp(preset.value(WarpParameter.COMPRESS), 0, 1);
            out[0] += detail * (dy + dx * compress);
            out[1] += detail * (-dx + dy * compress);
        } else {
            out[0] += detail * dx;
            out[1] += detail * dy;
        }
        stretch(x, y, preset, out);
    }

    private static void stretch(float x, float y, WarpPreset preset, float[] out) {
        float amount = Math.clamp(preset.value(WarpParameter.STRETCH), 0, 1);
        if (amount <= 1e-5f) {
            return;
        }
        float scale = Math.max(1f, Math.max(.01f, preset.value(WarpParameter.SCALE)) * .6f);
        int octaves = Math.clamp(Math.round(preset.value(WarpParameter.OCTAVES)) - 1, 1, 2);
        float phase = preset.value(WarpParameter.PHASE);
        float noise = fbm3(x * scale + 17.3f, y * scale - 9.1f,
                phase * 2f + 7.3f, preset.seed() + 911, octaves);
        float profile = smoothstep(.2f, .8f, noise);
        float gain = amount * (.25f + .75f * profile);
        out[0] += (x - .5f) * gain;
        out[1] += (y - .5f) * gain;
    }

    private static float fbm3(float x, float y, float z, int seed, int octaves) {
        float sum = 0;
        float frequency = 1;
        float amplitude = 1;
        float total = 0;
        for (int i = 0; i < octaves; i++) {
            float angle = .37f * i + .11f;
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            float rx = x * c - y * s;
            float ry = x * s + y * c;
            sum += valueNoise3(rx * frequency, ry * frequency,
                    z * frequency * .73f + i * 3.17f, seed + i * 131) * amplitude;
            total += amplitude;
            frequency *= 2.03f;
            amplitude *= .5f;
        }
        return total == 0 ? .5f : sum / total;
    }

    private static float valueNoise3(float x, float y, float z, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float u = fade(x - xi);
        float v = fade(y - yi);
        float w = fade(z - zi);
        float a000 = hash3(xi, yi, zi, seed);
        float a100 = hash3(xi + 1, yi, zi, seed);
        float a010 = hash3(xi, yi + 1, zi, seed);
        float a110 = hash3(xi + 1, yi + 1, zi, seed);
        float a001 = hash3(xi, yi, zi + 1, seed);
        float a101 = hash3(xi + 1, yi, zi + 1, seed);
        float a011 = hash3(xi, yi + 1, zi + 1, seed);
        float a111 = hash3(xi + 1, yi + 1, zi + 1, seed);
        float y0 = lerp(lerp(a000, a100, u), lerp(a010, a110, u), v);
        float y1 = lerp(lerp(a001, a101, u), lerp(a011, a111, u), v);
        return lerp(y0, y1, w);
    }

    private static float hash3(int x, int y, int z, int seed) {
        int h = seed;
        h = 31 * h + x;
        h = 31 * h + y;
        h = 31 * h + z;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0 : 1;
        }
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
