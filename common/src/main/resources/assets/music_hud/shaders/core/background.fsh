#version 150

layout(std140) uniform MHBasePosition {
    mat4 u_Translation;
    vec3 u_Layout; // (halfWidth, halfHeight, cornerRadius)
};
layout(std140) uniform MHNowPlayingThemeColor {
    vec4 u_Primary;
    vec4 u_Secondary;
    vec4 u_Bright;
    vec4 u_Dark;
};
layout(std140) uniform MHDynamicStatus {
    vec4 u_Dynamic1; // (timestamp, playedProgress, switchProgress)
};

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

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

float fbm(vec2 uv) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 4.0;
    for(int i = 0; i < 3; i++) {
        value += amplitude * snoise(uv * frequency);
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    return value * 0.5 + 0.5;
}

const float SPLIT_C0 = 0.2;
const float SPLIT_C1 = 0.36;
const float SPLIT_C2 = 0.64;
const float SPLIT_C3 = 0.8;

vec4 mix4Colors(vec4 c0, vec4 c1, vec4 c2, vec4 c3, float t) {
    t = clamp(t, SPLIT_C0, SPLIT_C3);
    float s0 = SPLIT_C0; float s1 = SPLIT_C1; float s2 = SPLIT_C2; float s3 = SPLIT_C3;
    s1 = clamp(s1, s0, s3); s2 = clamp(s2, s1, s3);
    if (t <= s1) {
        float f = (t - s0) / (s1 - s0); f = f * f * (3.0 - 2.0 * f); return mix(c0, c1, f);
    } else if (t <= s2) {
        float f = (t - s1) / (s2 - s1); f = f * f * (3.0 - 2.0 * f); return mix(c1, c2, f);
    } else {
        float f = (t - s2) / (s3 - s2); f = f * f * (3.0 - 2.0 * f); return mix(c2, c3, f);
    }
}

vec4 mix4ColorsDirectional(vec4 c0, vec4 c1, vec4 c2, vec4 c3, float t, float direction) {
    if (direction < 0.0) { t = 1.0 - t; vec4 tmp = c0; c0 = c3; c3 = tmp; tmp = c1; c1 = c2; c2 = tmp; }
    return mix4Colors(c0, c1, c2, c3, t);
}

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

void main() {
    float halfWidth  = u_Layout[0];
    float halfHeight = u_Layout[1];
    float radius     = u_Layout[2];
    float timestamp  = u_Dynamic1[0];

    vec2 uv = f_Position / vec2(halfWidth, halfHeight);
    float aspect = halfWidth / halfHeight;
    vec2 noiseUv = uv;
    noiseUv.x *= aspect;
    float speed = 0.014;
    vec2 scrollVec = vec2(timestamp * speed, timestamp * speed * 0.7);

    float wx = snoise(noiseUv * 0.015 + scrollVec * 0.3);
    float wy = snoise(noiseUv * 0.02 + scrollVec * 0.4 + vec2(2.7, 1.3));
    vec2 warped = noiseUv + vec2(wx, wy) * 3.0;

    float a = 0.03;
    float w0 = fbm(warped * (a) + scrollVec);
    float w1 = fbm(warped * (a + 0.004) - scrollVec * 0.6 + vec2(3.7, 5.2));
    float w2 = fbm(warped * (a + 0.002) + scrollVec * 0.5 + vec2(7.1, 2.9));
    float w3 = fbm(warped * (a + 0.006) + scrollVec * 0.4 + vec2(1.8, 6.4));

    w0 = smoothstep(0.15, 0.85, w0);
    w1 = smoothstep(0.15, 0.85, w1);
    w2 = smoothstep(0.15, 0.85, w2);
    w3 = smoothstep(0.15, 0.85, w3);

    float total = w0 + w1 + w2 + w3 + 0.001;
    w0 /= total; w1 /= total; w2 /= total; w3 /= total;

    vec3 r0 = u_Dark.rgb;
    vec3 r1 = u_Primary.rgb;
    vec3 r2 = u_Secondary.rgb;
    vec3 r3 = u_Bright.rgb;

    float dir = snoise(noiseUv * 0.02 + noiseUv.yx * 0.01 + timestamp * 0.01);
    float reverse = step(dir, 0.0);

    vec3 c0 = mix(r0, r3, reverse); vec3 c3 = mix(r3, r0, reverse);
    vec3 c1 = mix(r1, r2, reverse); vec3 c2 = mix(r2, r1, reverse);
    float rw0 = mix(w0, w3, reverse);
    float rw1 = mix(w1, w2, reverse);
    float rw2 = mix(w2, w1, reverse);
    float rw3 = mix(w3, w0, reverse);

    vec3 rgb = c0 * rw0 + c1 * rw1 + c2 * rw2 + c3 * rw3;
    float alpha = u_Dark.a * rw0 + u_Primary.a * rw1 + u_Secondary.a * rw2 + u_Bright.a * rw3;

    vec2 halfSize = vec2(halfWidth, halfHeight);
    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    fragColor = vec4(rgb, alpha * mask);
}
