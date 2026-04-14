#version 150

layout(std140) uniform HudBackgroundParams {
    mat4 u_Translation;               // unused
    vec4 u_RectParam;                 // (halfWidth, halfHeight, radius, timestamp)
    vec3 u_TransitionParam;           // (fadeProgress, nextImageAspect, imageAspect)
    mat4 u_BgColors;                  // 4 colors (column-major)
};

in vec2 f_Position;                   // coordinates relative to center, range [-halfWidth, halfHeight]
in vec4 f_Color;                      // vertex color (usually white, kept for compatibility)

out vec4 fragColor;

// ---------- 2D Simplex Noise Implementation (by Ian McEwan, Ashima Arts) ----------
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289(((x*34.0)+1.0)*x); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439,
    -0.577350269189626, 0.024390243902439);
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289(i);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

// Fractal Brownian Motion (FBM) – 3 octaves for rich detail
float fbm(vec2 uv) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 4.0;
    for(int i = 0; i < 3; i++) {
        value += amplitude * snoise(uv * frequency);
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    return value * 0.5 + 0.5;  // map from [-1,1] to [0,1]
}

const float SPLIT_C0 = 0.2;
const float SPLIT_C1 = 0.36;
const float SPLIT_C2 = 0.64;
const float SPLIT_C3 = 0.8;

vec4 mix4Colors(vec4 c0, vec4 c1, vec4 c2, vec4 c3, float t) {
    t = clamp(t, SPLIT_C0, SPLIT_C3);

    float s0 = SPLIT_C0;
    float s1 = SPLIT_C1;
    float s2 = SPLIT_C2;
    float s3 = SPLIT_C3;

    // 防止分界点顺序错误（可选的安全钳位）
    s1 = clamp(s1, s0, s3);
    s2 = clamp(s2, s1, s3);

    if (t <= s1) {
        // 第一段：范围 [s0, s1]
        float f = (t - s0) / (s1 - s0);
        f = f * f * (3.0 - 2.0 * f);
        return mix(c0, c1, f);
    } else if (t <= s2) {
        // 第二段：范围 [s1, s2]
        float f = (t - s1) / (s2 - s1);
        f = f * f * (3.0 - 2.0 * f);
        return mix(c1, c2, f);
    } else {
        // 第三段：范围 [s2, s3]
        float f = (t - s2) / (s3 - s2);
        f = f * f * (3.0 - 2.0 * f);
        return mix(c2, c3, f);
    }
}

vec4 mix4ColorsDirectional(vec4 c0, vec4 c1, vec4 c2, vec4 c3, float t, float direction) {
    // direction > 0 时正向 (c0→c3)，<0 时反向 (c3→c0)
    if (direction < 0.0) {
        // 反转 t 并交换颜色顺序
        t = 1.0 - t;
        vec4 tmp = c0; c0 = c3; c3 = tmp;
        tmp = c1; c1 = c2; c2 = tmp;
    }
    // 调用原来的 mix4Colors（常量分界版本）
    return mix4Colors(c0, c1, c2, c3, t);
}

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

void main() {
    // Extract uniform parameters
    float halfWidth  = u_RectParam.x;
    float halfHeight = u_RectParam.y;
    float radius     = u_RectParam.z;
    float timestamp  = u_RectParam.w;

    // Normalized coordinates (-1..1)
    vec2 uv = f_Position / vec2(halfWidth, halfHeight);

    float aspect = halfWidth / halfHeight;
    vec2 noiseUv = uv;
    noiseUv.x *= aspect;
    // Fluid-like distortion: scroll and scale the UVs
    float speed = 0.008;
    vec2 scrollVec = vec2(timestamp * speed, timestamp * speed * 0.7);
    vec2 uv0 = noiseUv * 0.02 + scrollVec;          // base noise
    vec2 uv1 = noiseUv * 0.04 - scrollVec * 1.3;    // second layer for complexity

    // Combine two FBM layers
    float noise1 = fbm(uv0);
    float noise2 = fbm(uv1);
    float finalNoise = (noise1 * 0.7 + noise2 * 0.3);

    // Retrieve the 4 colors from uniform (column-major matrix)
    vec4 primary = u_BgColors[0];
    vec4 secondary = u_BgColors[1];
    vec4 bright = u_BgColors[2];
    vec4 dark = u_BgColors[3];

    // Map noise to color gradient position
//    vec4 color = mix4Colors(dark, primary, secondary, bright, finalNoise);
    float dir = snoise(uv0 * 0.8 + timestamp * 0.2); // 动态变化的方向场
    vec4 color = mix4ColorsDirectional(dark, primary, secondary, bright, finalNoise, dir);

    // ---------- Rounded rectangle clipping (with antialiasing) ----------
    vec2 halfSize = vec2(halfWidth, halfHeight);
    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    // Apply alpha and optional vertex color modulation
    fragColor = vec4(color.rgb, color.a * mask);
}