#version 150

layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec4 u_Style;
};

// u_Adjust: (natural saturation, highlightDarkening, highlightKnee, highlightFull)
// u_Adjust2: (overallBrightness, applyColorAdjust, backgroundBlackOverlay, gammaToneMapping)
layout(std140) uniform MHBackgroundAdjust {
    vec4 u_Adjust;
    vec4 u_Adjust2;
};

uniform sampler2D Sampler0;

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

float ign(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

const vec3 LUMA_W = vec3(0.2126, 0.7152, 0.0722);

vec3 applyHighlightDarkening(vec3 rgb, float strength,
                             float highlightKnee, float highlightFull) {
    vec3 c = clamp(rgb, 0.0, 1.0);
    float value = max(c.r, max(c.g, c.b));

    float knee = clamp(highlightKnee, 0.0, 1.0);
    float full = max(highlightFull, knee + 1e-4);
    float highlight = smoothstep(knee, full, value);

    return rgb * (1.0 - clamp(strength, 0.0, 1.0) * highlight);
}

// Vibrance-style saturation: muted colors gain more chroma, already saturated colors
// gain little or none, avoiding the clipping of a traditional luma mix.
vec3 applyNaturalSaturation(vec3 rgb, float saturation) {
    float amount = saturation - 1.0;
    float maxC = max(rgb.r, max(rgb.g, rgb.b));
    float minC = min(rgb.r, min(rgb.g, rgb.b));
    float chroma = maxC - minC;
    float relative = maxC > 1e-4 ? chroma / maxC : 0.0;
    float weight = amount > 0.0 ? 1.0 - smoothstep(0.25, 0.9, relative) : 1.0;
    float luma = dot(rgb, LUMA_W);
    return vec3(luma) + (rgb - vec3(luma)) * (1.0 + amount * weight);
}

// Brightness is applied as a single scale factor so it stays independent from saturation.
// GAMMA applies the curve to the maximum channel instead of each channel separately.
vec3 applyBrightness(vec3 rgb, float brightness, float gammaMode) {
    float b = max(brightness, 0.0);
    if (gammaMode < 0.5) {
        return rgb * b;
    }
    if (b <= 1e-4) {
        return vec3(0.0);
    }
    float value = max(rgb.r, max(rgb.g, rgb.b));
    if (value <= 1e-4) {
        return vec3(0.0);
    }
    float mapped = pow(clamp(value, 0.0, 1.0), 1.0 / b);
    return rgb * (mapped / value);
}

void main() {
    vec2 halfSize = u_Style.xy;
    float radius = u_Style.z;

    vec2 uv = clamp(f_Position / (halfSize * 2.0) + 0.5, 0.0, 1.0);
    vec4 src = texture(Sampler0, uv);

    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    vec3 rgb = src.rgb;
    if (u_Adjust2.y > 0.5) {
        rgb = applyNaturalSaturation(rgb, u_Adjust.x);
        rgb = applyHighlightDarkening(rgb, u_Adjust.y, u_Adjust.z, u_Adjust.w);
        rgb = clamp(applyBrightness(rgb, u_Adjust2.x, u_Adjust2.w), 0.0, 1.0);
    }
    rgb *= 1.0 - clamp(u_Adjust2.z, 0.0, 1.0);
    rgb += (ign(gl_FragCoord.xy) - 0.5) * (2.0 / 255.0);
    rgb = clamp(rgb, 0.0, 1.0);

    fragColor = vec4(rgb, src.a * mask * u_Style.w);
}
