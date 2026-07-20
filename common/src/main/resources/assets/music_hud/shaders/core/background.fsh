#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

float hashFloat(float n) {
    return fract(sin(n) * 43758.5453123);
}

vec2 mirrorTileX(float tilePos, float yPos) {
    float mt = fract((tilePos + 1.0) / 2.0) * 2.0 - 1.0;
    return vec2(abs(mt), yPos - floor(yPos));
}

void main() {
    float halfWidth  = u_Layout[0];
    float halfHeight = u_Layout[1];
    float radius     = u_Layout[2];
    float timestamp  = u_Dynamic1[0];
    float fadeProgress = u_Dynamic1[2];

    vec2 halfSize = vec2(halfWidth, halfHeight);
    vec2 normalizedPos = (f_Position / halfSize) * 0.5 + 0.5;
    float hudAspect = halfSize.x / halfSize.y;

    float speed = 0.014;
    vec2 scrollVec = vec2(timestamp * speed, timestamp * speed * 0.7);

    float tileSpeed = 0.018;
    vec2 tileScroll = vec2(
        snoise(vec2(timestamp * tileSpeed, 0.0)),
        snoise(vec2(timestamp * tileSpeed * 0.7, 1.7))
    ) * 0.5;

    // mirror tiling on X-axis; short-edge fit on Y
    float tileCount = hudAspect;
    float tilePos = normalizedPos.x * tileCount + tileScroll.x;
    float tileIndex = floor(tilePos);
    float yPos = normalizedPos.y + tileScroll.y;

    // per-tile random Y phase offset to break grid alignment
    float yPhase = hashFloat(tileIndex + floor(timestamp * 0.1)) * 0.03;
    yPos += yPhase;

    vec2 baseUV = mirrorTileX(tilePos, yPos);

    // multi-octave displacement on HUD-space coords
    vec2 displace = vec2(0.0);
    float amp = 0.18;
    float freq = 0.7;
    for (int i = 0; i < 4; i++) {
        displace.x += snoise(normalizedPos * freq + scrollVec * (0.2 + float(i) * 0.15)) * amp;
        displace.y += snoise(normalizedPos * freq + scrollVec * (0.25 + float(i) * 0.15) + vec2(2.7, 1.3)) * amp;
        freq *= 2.4;
        amp *= 0.45;
    }

    vec2 warpedUV = baseUV + displace;

    // screen-pixel blur
    float stepX = length(vec2(dFdx(warpedUV.x), dFdy(warpedUV.x)));
    float stepY = length(vec2(dFdx(warpedUV.y), dFdy(warpedUV.y)));
    vec2 pixelStep = vec2(stepX, stepY) * 2.5;

    const int R = 3;
    vec4 blur0 = vec4(0.0);
    vec4 blur1 = vec4(0.0);
    float totalWeight = 0.0;
    for (int x = -R; x <= R; x++) {
        for (int y = -R; y <= R; y++) {
            vec2 offset = vec2(float(x), float(y)) * pixelStep;
            float w = 1.0 / (1.0 + float(x*x + y*y));
            blur0 += texture(Sampler0, fract(warpedUV + offset)) * w;
            blur1 += texture(Sampler1, fract(warpedUV + offset)) * w;
            totalWeight += w;
        }
    }
    blur0 /= totalWeight;
    blur1 /= totalWeight;

    float t = smoothstep(0.0, 1.0, fadeProgress);
    vec4 finalImage = mix(blur0, blur1, t);

    // atmospheric color wash
    vec3 wash = u_Dark.rgb * 0.4 + u_Primary.rgb * 0.15;
    finalImage.rgb = mix(finalImage.rgb, wash, 0.6);
    finalImage.rgb *= 0.75;

    // rounded rect mask
    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    fragColor = vec4(finalImage.rgb, finalImage.a * mask);
}
