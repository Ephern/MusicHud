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
    float speed = 0.008;
    vec2 scrollVec = vec2(timestamp * speed, timestamp * speed * 0.7);
    vec2 uv0 = noiseUv * 0.02 + scrollVec;
    vec2 uv1 = noiseUv * 0.04 - scrollVec * 1.3;

    float noise1 = fbm(uv0);
    float noise2 = fbm(uv1);
    float finalNoise = (noise1 * 0.7 + noise2 * 0.3);

    vec4 primary   = u_Primary;
    vec4 secondary = u_Secondary;
    vec4 bright    = u_Bright;
    vec4 dark      = u_Dark;

    float dir = snoise(uv0 * 0.8 + timestamp * 0.2);
    vec4 color = mix4ColorsDirectional(dark, primary, secondary, bright, finalNoise, dir);

    vec2 halfSize = vec2(halfWidth, halfHeight);
    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    fragColor = vec4(color.rgb, color.a * mask);
}
